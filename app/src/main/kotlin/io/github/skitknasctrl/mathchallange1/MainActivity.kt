/*
MathChallange-1 (spelet heter MYNTKORGEN i appen) – första spelbara prototypen
Ursprunglig kod skriven av ChatGPT; projektstruktur och bygge av Claude.

REGLER
Korgen flyttas med två aktiva fält i vänster och höger kant: 10 % av
skärmbredden vardera, i de mittersta 50 % av skärmens höjd. Håll fingret i
fältet så rör sig korgen åt det hållet. Fälten påverkar inte fallande mynt.
Korgen är långsam i början. Varje fångat mynt ger ett mynt i saldot och en
poäng i Fångat. Missgränsen avslutar omgången när den nås.
Det finns två köp: Storlek (större korg) och Fart (snabbare korg), tio nivåer
vardera. Tryck på knappen när saldot räcker. Köpesumman dras direkt och
mynten pausas. Välj ett av sex svar på huvudräkningsfrågan. Rätt svar ger
uppgraderingen. Fel svar eller slut på tiden förbrukar insatsen. Rätt svar ger
även återbetalning enligt:
insats × maximal återbetalningsprocent × andel återstående svarstid.
Nästa köp av samma sort kostar grundpriset gånger nästa nivånummer.
Korgens fart = startfart × (1 + 0,5 × fartnivå).
Mattesvårigheten ökar ett steg för var tredje köp (storlek + fart), upp till
nivå fem. Division har alltid heltalssvar.
Svårighetsgraden stegras: för varje hel spelminut faller mynten snabbare och
kommer tätare (myntfarten mest). Utan köpt fart hinner man inte med.

TESTINSTÄLLNINGAR
Finns på startskärmen och under Paus. Alla inställningar och rekordet sparas.
Spara inställningar startar om omgången. Avbryt behåller omgången.
Hastighet anges i spelpunkter/sekund; spelplanens bredd är 360 spelpunkter.
Myntflöde anger antal nya mynt per tio sekunder. Max mynt samtidigt är
ett separat tak; nya mynt hoppas över om taket nåtts.
Myntfart ökar varje hel aktiv spelminut med inställd procent, högst till fyra
gånger startvärdet; flödet ökar 60 % så snabbt, högst tre gånger. Sätt ökning
till noll för fasta värden under provspelning.
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
    var progression: Int = 20, var price: Int = 20, var discount: Int = 30,
    var basketSpeed: Int = 90
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
    private var sizeLevel = 0
    private var speedLevel = 0
    private var pending = 0 // vilket köp frågan gäller: 0 = större korg, 1 = snabbare korg
    private var heldLeft = false
    private var heldRight = false
    private var moveDir = 0
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
    private val startNote = "Håll i kanterna för att flytta korgen"
    private var note = startNote
    var active = true

    init {
        rules = Rules(prefs.getInt("misses", 10), prefs.getInt("speed", 180),
            prefs.getInt("flow", 12), prefs.getInt("math", 1), prefs.getInt("seconds", 10),
            prefs.getInt("maxCoins", 30), prefs.getInt("progression", 20),
            prefs.getInt("price", 20), prefs.getInt("discount", 30),
            prefs.getInt("basketSpeed", 90))
        isFocusable = true
    }
    fun resumeClock() { last = 0L; invalidate() }
    private fun reset() {
        coins.clear(); elapsed = 0f; spawn = 0f; balance = 0; caught = 0
        missed = 0; sizeLevel = 0; speedLevel = 0; basket = 180f; started = true; paused = false
        over = false; question = null; note = startNote; last = 0L
        invalidate()
    }
    private fun basketWidth() = min(300f, 66f + sizeLevel * 23f)
    private fun basketSpeed() = rules.basketSpeed * (1f + 0.5f * speedLevel)
    private fun levelOf(type: Int) = if (type == 0) sizeLevel else speedLevel
    private fun price(type: Int) = rules.price * (levelOf(type) + 1)
    private fun maxed(type: Int) = levelOf(type) >= 10
    // Aktiva fält: 10 % av bredden på var sida, i de mittersta 50 % av höjden.
    private fun zoneDir(x: Float, y: Float): Int {
        if (y < worldHeight * 0.25f || y > worldHeight * 0.75f) return 0
        return if (x <= 36f) -1 else if (x >= 324f) 1 else 0
    }
    private fun finish() {
        over = true
        if (caught > high) { high = caught; prefs.edit().putInt("high", high).apply() }
        note = "Spelet är slut"
    }
    private fun generateQuestion(): Question {
        val difficulty = min(5, rules.math + (sizeLevel + speedLevel) / 3)
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
    private fun buy(type: Int) {
        if (maxed(type) || balance < price(type)) return
        pending = type
        stake = price(type); balance -= stake
        question = generateQuestion()
        timeLeft = rules.seconds.toFloat(); purchaseSeconds = timeLeft
    }
    private fun answer(value: Int?) {
        val q = question ?: return
        if (value == q.answer) {
            val refund = (stake * rules.discount / 100f * (timeLeft / purchaseSeconds)).roundToInt()
            balance += refund
            if (pending == 0) {
                sizeLevel++
                basket = basket.coerceIn(basketWidth()/2, 360f-basketWidth()/2)
                note = "Större korg! Återbetalt: $refund mynt"
            } else {
                speedLevel++
                note = "Snabbare korg! Återbetalt: $refund mynt"
            }
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
        if (moveDir != 0) {
            basket = (basket + moveDir * basketSpeed() * dt).coerceIn(basketWidth()/2, 360f-basketWidth()/2)
        }
        // En ökning per hel spelminut, med tak för fortsatt spelbarhet.
        // Myntfarten ökar mest; flödet ökar 60 % så snabbt.
        val steps = floor(elapsed / 60f) * rules.progression / 100f
        val factor = min(4f, 1f + steps)
        val flowFactor = min(3f, 1f + steps * 0.6f)
        spawn += dt
        val interval = 10f / (rules.flow * flowFactor)
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
    private fun button(c: Canvas, label: String, r: RectF, enabled: Boolean = true, size: Float = 16f, action: () -> Unit) {
        panel(c, r, if (enabled) Color.rgb(35,112,133) else Color.rgb(45,53,67))
        text(c, label, r.centerX(), r.centerY()+size*0.375f, size,
            if (enabled) Color.WHITE else Color.LTGRAY)
        if (enabled) buttons.add(r to action)
    }
    // De två aktiva kantfälten. Ritas under mynten; mynt påverkas aldrig av fälten.
    private fun drawZones(c: Canvas) {
        val top = worldHeight * 0.25f
        val bottom = worldHeight * 0.75f
        for (side in intArrayOf(-1, 1)) {
            val r = if (side < 0) RectF(0f, top, 36f, bottom) else RectF(324f, top, 360f, bottom)
            val held = if (side < 0) heldLeft else heldRight
            paint.shader = null; paint.style = Paint.Style.FILL
            paint.color = Color.argb(if (held) 90 else 38, 255, 255, 255)
            c.drawRect(r, paint)
            val cx = r.centerX(); val cy = r.centerY()
            val arrow = Path()
            arrow.moveTo(cx + side * 10f, cy)
            arrow.lineTo(cx - side * 8f, cy - 14f)
            arrow.lineTo(cx - side * 8f, cy + 14f)
            arrow.close()
            paint.color = Color.argb(if (held) 220 else 120, 255, 255, 255)
            c.drawPath(arrow, paint)
        }
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
        text(c,"Missat: $missed / ${rules.misses}   Storlek: ${sizeLevel+1}   Fart: ${speedLevel+1}",180f,87f,13f)
        text(c,"Rekord: $high",180f,110f,12f,Color.LTGRAY)
        if (started && !over) drawZones(c)
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
        val playing = started && !over && !paused && question == null
        button(c,if(maxed(0)) "Storlek max" else "Storlek · ${price(0)}",RectF(8f,worldHeight-78f,120f,worldHeight-32f),playing && !maxed(0) && balance>=price(0),14f) { buy(0) }
        button(c,if(maxed(1)) "Fart max" else "Fart · ${price(1)}",RectF(124f,worldHeight-78f,236f,worldHeight-32f),playing && !maxed(1) && balance>=price(1),14f) { buy(1) }
        button(c,"Paus",RectF(240f,worldHeight-78f,352f,worldHeight-32f),started && !over && question==null) { paused=true }
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
            text(c,if(pending==0) "KÖP STÖRRE KORG" else "KÖP SNABBARE KORG",180f,top+25f,21f)
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
        val action = event.actionMasked
        val lifting = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP
        val ended = if (lifting) event.actionIndex else -1
        if (lifting) {
            // Knapptryck registreras när fingret lyfts, även om ett annat finger håller i ett kantfält.
            performClick()
            val x = event.getX(ended) / scale; val y = event.getY(ended) / scale
            buttons.firstOrNull { it.first.contains(x,y) }?.second?.invoke()
        }
        // Korgen styrs bara av fingrar som ligger i kantfälten; resten av skärmen gör ingenting.
        var left = false; var right = false
        if (action != MotionEvent.ACTION_CANCEL) {
            for (i in 0 until event.pointerCount) {
                if (i == ended) continue
                when (zoneDir(event.getX(i) / scale, event.getY(i) / scale)) {
                    -1 -> left = true
                    1 -> right = true
                }
            }
        }
        heldLeft = left; heldRight = right
        moveDir = (if (right) 1 else 0) - (if (left) 1 else 0)
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
        field("basketSpeed","Korgens startfart, spelpunkter/sekund (20–400)",rules.basketSpeed)
        val scroll=ScrollView(host).apply { addView(content) }
        val dialog=AlertDialog.Builder(host).setTitle("Testinställningar").setView(scroll)
            .setMessage("Spara startar en ny omgång. Inställningarna sparas mellan appstarter.")
            .setNegativeButton("Avbryt",null).setPositiveButton("Spara & börja om",null).create()
        dialog.setOnDismissListener { settingsOpen=false; last=0L; invalidate() }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val bounds=listOf(1..999,40..800,1..100,1..100,1..5,2..60,0..50,1..500,0..100,20..400)
                val values=fields.values.map { it.text.toString().toIntOrNull() }
                var valid=true
                fields.values.forEachIndexed { i, input -> if(values[i]==null || values[i]!! !in bounds[i]) { input.error="Ange ${bounds[i].first}–${bounds[i].last}"; valid=false } }
                if(valid) {
                    val v=values.map { it!! }
                    rules=Rules(v[0],v[1],v[2],v[4],v[5],v[3],v[6],v[7],v[8],v[9])
                    val edit=prefs.edit(); fields.keys.forEachIndexed { i,key -> edit.putInt(key,v[i]) }; edit.apply()
                    reset(); dialog.dismiss()
                }
            }
        }
        dialog.show()
    }
}
