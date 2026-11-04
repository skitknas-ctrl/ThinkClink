/*
MathChallange-1 (spelet heter MYNTKORGEN i appen) – första spelbara prototypen
Ursprunglig kod skriven av ChatGPT; projektstruktur och bygge av Claude.

REGLER
Dra korgen åt sidan för att fånga mynt. Varje fångat mynt ger ett mynt i
saldot och en poäng i Fångat. Missgränsen avslutar omgången när den nås.
Tryck Köp korg när saldot räcker. Köpesumman dras direkt och mynten pausas.
Välj ett av sex svar. Rätt svar ger större korg. Fel svar eller slut på tiden
förbrukar insatsen. Rätt svar ger även återbetalning enligt:
insats × maximal återbetalningsprocent × andel återstående svarstid.
Nästa korg kostar grundpriset gånger nästa uppgraderingsnummer.
Det finns tio uppgraderingar. Mattesvårigheten ökar ett steg varannan
uppgradering, upp till nivå fem. Division har alltid heltalssvar.

TESTINSTÄLLNINGAR
Finns på startskärmen och under Paus. Alla inställningar och rekordet sparas.
Spara inställningar startar om omgången. Avbryt behåller omgången.
Hastighet anges i spelpunkter/sekund; spelplanens bredd är 360 spelpunkter.
Myntflöde anger antal nya mynt per tio sekunder. Max mynt samtidigt är
ett separat tak; nya mynt hoppas över om taket nåtts.
Fart och flöde ökar varje hel aktiv spelminut, högst till tre gånger
startvärdena. Sätt ökning till noll för fasta värden under provspelning.
Mattesvårighet: 1 plus/minus upp till 10; 2 plus/minus upp till 20 och
gånger; 3 plus/minus upp till 50 och gånger; 4–5 större tal och division.

BEGRÄNSNINGAR OCH KONTROLL
Omgången finns i minnet. Appen är låst i stående läge och hanterar rotation
själv, så omgången börjar inte om vid vridning. Inställningar och sparat
rekord finns kvar mellan appstarter. Appbyte pausar spelet och frågetiden.
Rekord sparas när en omgång avslutas av missgränsen.
Grafiken är ritad med Android Canvas, utan separata bildfiler.
Innehållet läggs innanför statusfält och navigeringsfält (se applyBarInsets).
Referens: https://developer.android.com/develop/ui/views/layout/custom-views/custom-drawing
*/

package io.github.skitknasctrl.mathchallange1

import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.graphics.*
import android.view.*
import android.widget.*
import kotlin.math.*
import kotlin.random.Random

class MainActivity : Activity() {
    private lateinit var game: CoinGame
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.black)
        val root = android.widget.FrameLayout(this)
        // Android 15 (targetSdk 35) tvingar kant-till-kant: utan detta hamnar spelet under statusfältet.
        root.setOnApplyWindowInsetsListener { view, insets -> applyBarInsets(view, insets); insets }
        game = CoinGame(this)
        root.addView(game)
        setContentView(root)
    }

    @Suppress("DEPRECATION")
    private fun applyBarInsets(view: View, insets: WindowInsets) {
        if (Build.VERSION.SDK_INT >= 30) {
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        } else {
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
        }
    }
    override fun onPause() { super.onPause(); game.active = false }
    override fun onResume() { super.onResume(); if (::game.isInitialized) { game.active = true; game.resumeClock() } }
}

private data class Rules(
    var misses: Int = 10, var speed: Int = 180, var flow: Int = 12,
    var math: Int = 1, var seconds: Int = 10, var maxCoins: Int = 30,
    var progression: Int = 15, var price: Int = 20, var discount: Int = 30
)
private data class Coin(var x: Float, var y: Float)
private data class Question(val text: String, val answer: Int, val options: List<Int>)

private class CoinGame(val host: Activity) : View(host) {
    private val prefs = host.getSharedPreferences("myntkorgen", 0)
    private var rules = Rules()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val coins = mutableListOf<Coin>()
    private val buttons = mutableListOf<Pair<RectF, () -> Unit>>()
    private var scale = 1f
    private var worldHeight = 800f
    private var last = 0L
    private var elapsed = 0f
    private var spawn = 0f
    private var balance = 0
    private var caught = 0
    private var missed = 0
    private var level = 0
    private var basket = 180f
    private var started = false
    private var paused = false
    private var over = false
    private var settingsOpen = false
    private var question: Question? = null
    private var timeLeft = 0f
    private var purchaseSeconds = 0f
    private var stake = 0
    private var high = prefs.getInt("high", 0)
    private var note = "Dra korgen med fingret"
    var active = true

    init {
        rules = Rules(prefs.getInt("misses", 10), prefs.getInt("speed", 180),
            prefs.getInt("flow", 12), prefs.getInt("math", 1), prefs.getInt("seconds", 10),
            prefs.getInt("maxCoins", 30), prefs.getInt("progression", 15),
            prefs.getInt("price", 20), prefs.getInt("discount", 30))
        isFocusable = true
    }
    fun resumeClock() { last = 0L; invalidate() }
    private fun reset() {
        coins.clear(); elapsed = 0f; spawn = 0f; balance = 0; caught = 0
        missed = 0; level = 0; basket = 180f; started = true; paused = false
        over = false; question = null; note = "Dra korgen med fingret"; last = 0L
        invalidate()
    }
    private fun basketWidth() = min(300f, 66f + level * 23f)
    private fun price() = rules.price * (level + 1)
    private fun finish() {
        over = true
        if (caught > high) { high = caught; prefs.edit().putInt("high", high).apply() }
        note = "Spelet är slut"
    }
    private fun generateQuestion(): Question {
        val difficulty = min(5, rules.math + level / 2)
        val limit = when (difficulty) { 1 -> 10; 2 -> 20; 3 -> 50; 4 -> 100; else -> 200 }
        val a = Random.nextInt(1, limit + 1)
        val b = Random.nextInt(1, limit + 1)
        val operation = Random.nextInt(if (difficulty == 1) 2 else if (difficulty < 4) 3 else 4)
        val pair = when (operation) {
            0 -> "$a + $b" to a + b
            1 -> "${max(a,b)} − ${min(a,b)}" to abs(a-b)
            2 -> { val x = Random.nextInt(2, min(20, limit) + 1); val y = Random.nextInt(2, 13); "$x × $y" to x*y }
            else -> { val x = Random.nextInt(2, 16); val y = Random.nextInt(2, 13); "${x*y} ÷ $y" to x }
        }
        val choices = mutableSetOf(pair.second)
        val spread = max(6, pair.second / 4)
        while (choices.size < 6) {
            val candidate = pair.second + Random.nextInt(-spread, spread + 1)
            if (candidate >= 0) choices.add(candidate)
        }
        return Question(pair.first, pair.second, choices.shuffled())
    }
    private fun buy() {
        if (balance < price() || level >= 10) return
        stake = price(); balance -= stake
        question = generateQuestion()
        timeLeft = rules.seconds.toFloat(); purchaseSeconds = timeLeft
    }
    private fun answer(value: Int?) {
        val q = question ?: return
        if (value == q.answer) {
            val refund = (stake * rules.discount / 100f * (timeLeft / purchaseSeconds)).roundToInt()
            balance += refund; level++
            basket = basket.coerceIn(basketWidth()/2, 360f-basketWidth()/2)
            note = "Större korg! Återbetalt: $refund mynt"
        } else note = if (value == null) "Tiden slut! Du förlorade $stake mynt" else "Fel! Rätt svar: ${q.answer}. Förlorat: $stake"
        question = null
    }
    private fun tick(dt: Float) {
        if (!started || paused || over || settingsOpen) return
        if (question != null) {
            timeLeft = max(0f, timeLeft-dt)
            if (timeLeft <= 0f) answer(null)
            return
        }
        elapsed += dt
        // En ökning per hel spelminut, med ett tak för fortsatt spelbarhet.
        val factor = min(3f, 1f + floor(elapsed / 60f) * rules.progression / 100f)
        spawn += dt
        val interval = 10f / (rules.flow * factor)
        while (spawn >= interval) {
            spawn -= interval
            if (coins.size < rules.maxCoins) coins.add(Coin(Random.nextFloat()*332f+14f, 135f))
        }
        val iterator = coins.iterator()
        val rim = worldHeight - 157f
        while (iterator.hasNext()) {
            val coin = iterator.next()
            val old = coin.y
            coin.y += rules.speed * factor * dt
            if (old <= rim && coin.y >= rim && abs(coin.x-basket) <= basketWidth()/2) {
                balance++; caught++; iterator.remove()
            } else if (coin.y > worldHeight - 108f) {
                missed++; iterator.remove()
                if (missed >= rules.misses) { finish(); break }
            }
        }
    }
    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float = 16f, color: Int = Color.WHITE) {
        paint.shader = null; paint.style = Paint.Style.FILL; paint.color = color
        paint.textSize = size; paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER; c.drawText(s, x, y, paint)
    }
    private fun panel(c: Canvas, r: RectF, color: Int) {
        paint.shader = null; paint.style = Paint.Style.FILL; paint.color = color
        c.drawRoundRect(r, 14f, 14f, paint)
    }
    private fun button(c: Canvas, label: String, r: RectF, enabled: Boolean = true, action: () -> Unit) {
        panel(c, r, if (enabled) Color.rgb(35,112,133) else Color.rgb(45,53,67))
        text(c, label, r.centerX(), r.centerY()+6f, 16f,
            if (enabled) Color.WHITE else Color.LTGRAY)
        if (enabled) buttons.add(r to action)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        scale = width / 360f; worldHeight = height / scale
        val now = System.nanoTime()
        val dt = if (last == 0L || !active) 0f else min(0.05f, (now-last)/1_000_000_000f)
        last = now
        if (active) tick(dt)
        canvas.save(); canvas.scale(scale, scale)
        val c = canvas
        buttons.clear()
        paint.shader = LinearGradient(0f,0f,0f,worldHeight,Color.rgb(10,18,39),Color.rgb(28,45,70),Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,360f,worldHeight,paint); paint.shader = null
        paint.color = Color.rgb(45,63,88)
        for (i in 0..35) c.drawCircle((i*83%360).toFloat(), (145+i*61%(max(1, worldHeight.toInt()-300))).toFloat(), 1.5f, paint)
        text(c,"MYNTKORGEN",180f,32f,24f,Color.rgb(255,209,93))
        text(c,"Mynt: $balance     Fångat: $caught",180f,62f)
        text(c,"Missat: $missed / ${rules.misses}     Korg: ${level+1}",180f,87f,15f)
        text(c,"Rekord: $high",180f,110f,12f,Color.LTGRAY)
        for (coin in coins) {
            paint.color = Color.rgb(239,166,38); c.drawCircle(coin.x,coin.y,11f,paint)
            paint.color = Color.rgb(255,220,105); c.drawCircle(coin.x-1f,coin.y-1f,8f,paint)
            text(c,"1",coin.x,coin.y+4f,12f,Color.rgb(136,84,10))
        }
        val bw = basketWidth(); val rim = worldHeight-157f
        panel(c,RectF(basket-bw/2,rim,basket+bw/2,rim+38f),Color.rgb(178,107,48))
        paint.color = Color.rgb(231,171,90)
        c.drawRect(basket-bw/2-3,rim,basket+bw/2+3,rim+6f,paint)
        text(c,note,180f,worldHeight-94f,11f,Color.rgb(255,219,137))
        button(c,if(level>=10) "Maximal korg" else "Köp korg · ${price()}",RectF(12f,worldHeight-78f,235f,worldHeight-32f),started && !over && !paused && question==null && balance>=price() && level<10) { buy() }
        button(c,"Paus",RectF(243f,worldHeight-78f,348f,worldHeight-32f),started && !over && question==null) { paused=true }
        if (!started || over || paused) {
            paint.color = Color.argb(220,7,13,29); c.drawRect(0f,122f,360f,worldHeight-90f,paint)
            val middle = (worldHeight+60f)/2
            text(c,if(over) "SPELET SLUT" else if(paused) "PAUS" else "FÅNGA & RÄKNA",180f,middle-110f,25f)
            text(c,if(over) "Du fångade $caught mynt" else "Fånga mynt. Räkna till dig större korgar.",180f,middle-77f,14f)
            button(c,if(paused && !over) "Fortsätt" else "Spela",RectF(55f,middle-48f,305f,middle)) { if(paused && !over) paused=false else reset() }
            button(c,"Testinställningar",RectF(55f,middle+14f,305f,middle+62f)) { settings() }
            if(paused) button(c,"Börja om",RectF(55f,middle+76f,305f,middle+124f)) { reset() }
        }
        question?.let { q ->
            buttons.clear()
            paint.color = Color.argb(245,10,18,39); c.drawRect(0f,122f,360f,worldHeight,paint)
            val top = max(130f, (worldHeight-340f)/2)
            text(c,"KÖP STÖRRE KORG",180f,top+25f,21f)
            text(c,"Insats: $stake mynt",180f,top+53f,15f)
            text(c,q.text+" = ?",180f,top+103f,34f,Color.rgb(255,209,93))
            text(c,String.format(java.util.Locale.forLanguageTag("sv-SE"),"%.1f sekunder kvar",timeLeft),180f,top+139f,18f)
            panel(c,RectF(30f,top+155f,330f,top+162f),Color.DKGRAY)
            panel(c,RectF(30f,top+155f,30f+300f*timeLeft/purchaseSeconds,top+162f),Color.rgb(78,211,178))
            q.options.forEachIndexed { i, option ->
                val x=25f+(i%2)*160f; val y=top+180f+(i/2)*58f
                button(c,option.toString(),RectF(x,y,x+150f,y+48f)) { answer(option) }
            }
        }
        canvas.restore()
        if (active) postInvalidateOnAnimation()
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x / scale; val y = event.y / scale
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            performClick()
            buttons.firstOrNull { it.first.contains(x,y) }?.second?.invoke()
        }
        if ((event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) && started && !over && !paused && question==null && y>125f && y<worldHeight-85f) {
            basket=x.coerceIn(basketWidth()/2,360f-basketWidth()/2)
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    private fun settings() {
        if(settingsOpen) return
        settingsOpen=true
        val content = LinearLayout(host).apply { orientation=LinearLayout.VERTICAL; setPadding(30,12,30,12) }
        val fields = linkedMapOf<String, EditText>()
        fun field(key: String, label: String, value: Int) {
            content.addView(TextView(host).apply { text=label })
            val input=EditText(host).apply { setText(value.toString()); inputType=2; setSingleLine() }
            fields[key]=input; content.addView(input)
        }
        field("misses","Tillåtna missar (1–999)",rules.misses)
        field("speed","Fallhastighet, spelpunkter/sekund (40–800)",rules.speed)
        field("flow","Mynt per 10 sekunder (1–100)",rules.flow)
        field("maxCoins","Max mynt samtidigt på skärmen (1–100)",rules.maxCoins)
        field("math","Mattesvårighet, startnivå (1–5)",rules.math)
        field("seconds","Svarstid, sekunder (2–60)",rules.seconds)
        field("progression","Ökning av fart/flöde per minut, % (0–50)",rules.progression)
        field("price","Pris för första uppgraderingen (1–500)",rules.price)
        field("discount","Max återbetalning vid snabbt rätt svar, % (0–100)",rules.discount)
        val scroll=ScrollView(host).apply { addView(content) }
        val dialog=AlertDialog.Builder(host).setTitle("Testinställningar").setView(scroll)
            .setMessage("Spara startar en ny omgång. Inställningarna sparas mellan appstarter.")
            .setNegativeButton("Avbryt",null).setPositiveButton("Spara & börja om",null).create()
        dialog.setOnDismissListener { settingsOpen=false; last=0L; invalidate() }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val bounds=listOf(1..999,40..800,1..100,1..100,1..5,2..60,0..50,1..500,0..100)
                val values=fields.values.map { it.text.toString().toIntOrNull() }
                var valid=true
                fields.values.forEachIndexed { i, input -> if(values[i]==null || values[i]!! !in bounds[i]) { input.error="Ange ${bounds[i].first}–${bounds[i].last}"; valid=false } }
                if(valid) {
                    val v=values.map { it!! }
                    rules=Rules(v[0],v[1],v[2],v[4],v[5],v[3],v[6],v[7],v[8])
                    val edit=prefs.edit(); fields.keys.forEachIndexed { i,key -> edit.putInt(key,v[i]) }; edit.apply()
                    reset(); dialog.dismiss()
                }
            }
        }
        dialog.show()
    }
}
