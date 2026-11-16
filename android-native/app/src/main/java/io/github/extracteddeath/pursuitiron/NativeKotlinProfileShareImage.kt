package io.github.extracteddeath.pursuitiron

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** This palette comes from the active Material theme; no hard-coded neon default. */
internal data class NativeProfileShareImageColors(
    val background:Int,
    val surface:Int,
    val foreground:Int,
    val muted:Int,
    val accent:Int
)

/**
 * Export exactly the reviewed, privacy-filtered summary as an image.
 *
 * No history or athlete storage is read here. FileProvider exposes only this
 * temporary cache subdirectory, and only after the athlete taps Share image.
 */
internal object NativeKotlinProfileShareImage {
    const val WIDTH=1080
    private const val MARGIN=92f
    private const val LINE_HEIGHT=58f
    private const val PARAGRAPH_GAP=26f

    fun render(summary:String,colors:NativeProfileShareImageColors):Bitmap {
        require(summary.isNotBlank())
        val body=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=colors.foreground
            textSize=40f
            typeface=Typeface.create("sans-serif",Typeface.NORMAL)
        }
        val blocks=summary.lineSequence().drop(1).filter { it.isNotBlank() }
            .map { wrap(it,body,WIDTH-2*MARGIN) }.toList()
        val contentHeight=blocks.sumOf { it.size*LINE_HEIGHT+PARAGRAPH_GAP }
        val height=maxOf(920,(318f+contentHeight+152f).toInt())
        val bitmap=Bitmap.createBitmap(WIDTH,height,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        canvas.drawColor(colors.background)
        val card=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=colors.surface}
        canvas.drawRoundRect(RectF(28f,28f,WIDTH-28f,height-28f),52f,52f,card)
        val accent=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=colors.accent}
        canvas.drawRoundRect(RectF(MARGIN,92f,MARGIN+86f,102f),5f,5f,accent)

        val brand=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=colors.foreground
            textSize=58f
            typeface=Typeface.create("sans-serif",Typeface.BOLD)
        }
        canvas.drawText("PURSUIT IRON",MARGIN,181f,brand)
        val subtitle=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=colors.muted
            textSize=29f
            typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        }
        canvas.drawText("TRAINING SUMMARY",MARGIN,236f,subtitle)
        canvas.drawRoundRect(
            RectF(MARGIN,266f,WIDTH-MARGIN,269f),1.5f,1.5f,accent)
        var y=332f
        blocks.forEach { lines ->
            lines.forEach { line ->
                canvas.drawText(line,MARGIN,y,body)
                y+=LINE_HEIGHT
            }
            y+=PARAGRAPH_GAP
        }
        // The source disclaimer is already in blocks. No additional
        // achievements, records, ratings or performance claims are drawn.
        return bitmap
    }

    /** Width-based rather than character-count wrapping prevents PNG cropping. */
    private fun wrap(value:String,paint:Paint,maxWidth:Float):List<String> {
        val result=mutableListOf<String>()
        var line=""
        for(word in value.trim().split(Regex("\\s+"))) {
            if(word.isEmpty()) continue
            val candidate=if(line.isEmpty()) word else "$line $word"
            if(paint.measureText(candidate)<=maxWidth) {
                line=candidate
                continue
            }
            if(line.isNotEmpty()) {
                result+=line
                line=""
            }
            if(paint.measureText(word)<=maxWidth) {
                line=word
            } else {
                var rest=word
                while(rest.isNotEmpty()) {
                    val take=paint.breakText(rest,true,maxWidth,null).coerceAtLeast(1)
                    val piece=rest.take(take)
                    rest=rest.drop(take)
                    if(rest.isEmpty()) line=piece else result+=piece
                }
            }
        }
        if(line.isNotEmpty()) result+=line
        return result.ifEmpty { listOf("") }
    }

    fun writeForExplicitShare(context:Context,bitmap:Bitmap):Uri {
        val dir=File(context.cacheDir,"profile_shares")
        check(dir.isDirectory || dir.mkdirs()) {
            "Cannot create temporary image sharing directory"
        }
        // Clean old local exports opportunistically, never in background.
        val cutoff=System.currentTimeMillis()-24L*60L*60L*1000L
        dir.listFiles()?.filter { it.extension=="png" && it.lastModified()<cutoff }
            ?.forEach { it.delete() }
        val output=File(dir,"training-summary-"+UUID.randomUUID()+".png")
        try {
            FileOutputStream(output).use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream)) {
                    "Could not encode profile image"
                }
            }
            return FileProvider.getUriForFile(
                context,context.packageName+".profile-share",output)
        } catch(error:Exception) {
            output.delete()
            throw error
        }
    }
}
