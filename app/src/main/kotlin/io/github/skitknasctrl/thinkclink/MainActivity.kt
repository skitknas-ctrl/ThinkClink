/*
ThinkClink

Fånga fallande mynt med en korg som styrs med två osynliga fält (vänster och höger halva). Varje nivå är
120 sekunder och blir svårare medan den pågår. Mellan nivåerna och när man
tappar ett liv öppnas en butik: snabbare korg (kräver rätt svar på en
mattefråga), större korg (för ädelstenar), liv och matte för pengar eller liv.
Alla regler och siffror står i DESIGN.md i repots rot.

Filer:
- MainActivity.kt  aktiviteten, placerar spelet innanför status- och navigeringsfältet
- GameView.kt      Android-delen: bildloop, pekhantering, sparning, ljud, testinställningar
- Engine.kt        alla spelregler och allt spelläge (ren Kotlin, inga Android-klasser)
- Renderer.kt      ritar spelet via Gfx
- Draggers.kt      de 31 dragarna (gubbe … jetmotorer) i vektorgrafik
- Gfx.kt           enkelt rit-gränssnitt; AndroidGfx.kt är Android-versionen
- Rules.kt         testinställningarna med standardvärden och gränser
- MathQuiz.kt      mattefrågorna
- Synth.kt, Sound.kt  ljudeffekter som skapas i koden

Ursprunglig idé och första kod: ChatGPT. Nuvarande version: Claude.
*/

package io.github.skitknasctrl.thinkclink

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout

class MainActivity : Activity() {
    private lateinit var game: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.black)
        val root = FrameLayout(this)
        // Android 15 (targetSdk 35) draws edge to edge: without this the game would end up under the status bar.
        root.setOnApplyWindowInsetsListener { view, insets -> applyBarInsets(view, insets); insets }
        game = GameView(this)
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

    override fun onPause() { super.onPause(); game.onHostPause() }
    override fun onResume() { super.onResume(); if (::game.isInitialized) game.onHostResume() }
    override fun onDestroy() { super.onDestroy(); if (::game.isInitialized) game.release() }
}
