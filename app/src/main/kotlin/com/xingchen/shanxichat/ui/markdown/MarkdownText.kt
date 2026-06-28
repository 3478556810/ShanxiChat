package com.xingchen.shanxichat.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class Code(val language: String, val code: String) : MarkdownBlock()
    data class Quote(val text: String) : MarkdownBlock()
    data class Bullet(val text: String, val indent: Int = 0) : MarkdownBlock()
    data class Numbered(val number: Int, val text: String, val indent: Int = 0) : MarkdownBlock()
    object Divider : MarkdownBlock()
    data class Image(val alt: String, val url: String) : MarkdownBlock()
    data class LatexBlock(val formula: String) : MarkdownBlock()
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false
) {
    // ★ 无论是否在流式状态，都实时进行块级解析。
    // 流式状态下后台解析可能带来轻微性能压力，但对于1.5B模型的输出速度完全可接受。
    var blocks by remember { mutableStateOf<List<MarkdownBlock>>(emptyList()) }
    LaunchedEffect(markdown) {
        blocks = withContext(Dispatchers.Default) {
            parseMarkdownSimple(markdown)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> HeadingBlock(block)
                is MarkdownBlock.Paragraph -> ParagraphBlock(block)
                is MarkdownBlock.Code -> CodeBlock(block)
                is MarkdownBlock.Quote -> QuoteBlock(block)
                is MarkdownBlock.Bullet -> BulletBlock(block)
                is MarkdownBlock.Numbered -> NumberedBlock(block)
                is MarkdownBlock.Divider -> DividerBlock()
                is MarkdownBlock.LatexBlock -> LatexBlockDisplay(block)
                is MarkdownBlock.Image -> ImageBlock(block)
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
        // 流式状态下，可以在最后加一个光标（可选）
        if (isStreaming) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "●", // 呼吸光标
                color = Color(0xFF2A2118).copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }
    }
}

// ── 块级渲染组件 ──
@Composable
private fun HeadingBlock(block: MarkdownBlock.Heading) {
    val fontSize = when (block.level) {
        1 -> 24.sp
        2 -> 20.sp
        3 -> 18.sp
        else -> 16.sp
    }
    Text(
        text = block.text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (block.level == 1) 16.dp else 12.dp, bottom = 4.dp),
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        lineHeight = (fontSize.value * 1.4).sp,
        color = Color(0xFF1A130A)
    )
}

@Composable
private fun ParagraphBlock(block: MarkdownBlock.Paragraph) {
    Text(
        text = buildInlineAnnotatedString(block.text),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        fontFamily = FontFamily.Serif,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        color = Color(0xFF2A2118)
    )
}

@Composable
private fun CodeBlock(block: MarkdownBlock.Code) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF7F6F3), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column {
            if (block.language.isNotEmpty()) {
                Text(
                    text = block.language,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFF6B635B)
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            Text(
                text = block.code,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = Color(0xFF2A2118)
            )
        }
    }
}

@Composable
private fun QuoteBlock(block: MarkdownBlock.Quote) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F2EE), RoundedCornerShape(6.dp))
            .padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(32.dp)
                .background(Color(0xFFD2C8BC), RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = block.text,
            fontFamily = FontFamily.Serif,
            fontSize = 15.sp,
            lineHeight = 24.sp,
            color = Color(0xFF4B4036),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BulletBlock(block: MarkdownBlock.Bullet) {
    Row(modifier = Modifier.fillMaxWidth().padding(start = (block.indent * 12).dp)) {
        Text(
            text = "•",
            fontFamily = FontFamily.Serif,
            fontSize = 16.sp,
            color = Color(0xFF2A2118),
            modifier = Modifier.width(18.dp)
        )
        Text(
            text = buildInlineAnnotatedString(block.text),
            fontFamily = FontFamily.Serif,
            fontSize = 16.sp,
            lineHeight = 26.sp,
            color = Color(0xFF2A2118),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun NumberedBlock(block: MarkdownBlock.Numbered) {
    Row(modifier = Modifier.fillMaxWidth().padding(start = (block.indent * 12).dp)) {
        Text(
            text = "${block.number}.",
            fontFamily = FontFamily.Serif,
            fontSize = 16.sp,
            color = Color(0xFF2A2118),
            modifier = Modifier.width(24.dp)
        )
        Text(
            text = buildInlineAnnotatedString(block.text),
            fontFamily = FontFamily.Serif,
            fontSize = 16.sp,
            lineHeight = 26.sp,
            color = Color(0xFF2A2118),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DividerBlock() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(vertical = 8.dp)
            .background(Color(0xFFE8E3DB))
    )
}

@Composable
private fun LatexBlockDisplay(block: MarkdownBlock.LatexBlock) {
    Text(
        text = block.formula,
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        color = Color(0xFF2A2118),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFDFCF9), RoundedCornerShape(6.dp))
            .padding(10.dp)
    )
}

@Composable
private fun ImageBlock(block: MarkdownBlock.Image) {
    Text(
        text = "[图片: ${block.alt.ifEmpty { block.url }}]",
        fontFamily = FontFamily.Serif,
        fontSize = 14.sp,
        color = Color(0xFF8B8279),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF7F6F3), RoundedCornerShape(8.dp))
            .padding(10.dp)
    )
}

// ── 解析器 ──
private fun parseMarkdownSimple(text: String): List<MarkdownBlock> {
    val lines = text.split("\n")
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i].trimEnd()
        when {
            line.trimStart().startsWith("```") -> {
                val lang = line.trimStart().removePrefix("```").trim()
                val sb = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    sb.appendLine(lines[i])
                    i++
                }
                blocks.add(MarkdownBlock.Code(lang, sb.toString().trimEnd()))
                i++
            }
            line.startsWith("###### ") -> { blocks.add(MarkdownBlock.Heading(6, line.drop(7))); i++ }
            line.startsWith("##### ") -> { blocks.add(MarkdownBlock.Heading(5, line.drop(6))); i++ }
            line.startsWith("#### ") -> { blocks.add(MarkdownBlock.Heading(4, line.drop(5))); i++ }
            line.startsWith("### ") -> { blocks.add(MarkdownBlock.Heading(3, line.drop(4))); i++ }
            line.startsWith("## ") -> { blocks.add(MarkdownBlock.Heading(2, line.drop(3))); i++ }
            line.startsWith("# ") -> { blocks.add(MarkdownBlock.Heading(1, line.drop(2))); i++ }
            line.startsWith("> ") -> { blocks.add(MarkdownBlock.Quote(line.drop(2))); i++ }
            line.startsWith("- ") -> { blocks.add(MarkdownBlock.Bullet(line.drop(2))); i++ }
            line == "---" || line == "***" -> { blocks.add(MarkdownBlock.Divider); i++ }
            line.matches(Regex("^\\d+\\.\\s.*")) -> {
                val num = line.substringBefore(".").toInt()
                val content = line.substringAfter(". ").trim()
                blocks.add(MarkdownBlock.Numbered(num, content))
                i++
            }
            line.isBlank() -> { i++ }
            else -> {
                val sb = StringBuilder()
                while (i < lines.size &&
                    lines[i].isNotBlank() &&
                    !lines[i].trimStart().startsWith("```") &&
                    !lines[i].startsWith("#") &&
                    !lines[i].startsWith("> ") &&
                    !lines[i].startsWith("- ") &&
                    !lines[i].matches(Regex("^\\d+\\.\\s.*"))
                ) {
                    sb.appendLine(lines[i])
                    i++
                }
                val paragraphText = sb.toString().trimEnd()
                if (paragraphText.isNotBlank()) {
                    blocks.add(MarkdownBlock.Paragraph(paragraphText))
                }
            }
        }
    }
    return blocks
}

// ── 行内样式解析 ──
@Composable
private fun buildInlineAnnotatedString(text: String) = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            text.startsWith("*", i) && !text.startsWith("**", i) -> {
                val end = text.indexOf("*", i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            text.startsWith("`", i) -> {
                val end = text.indexOf("`", i + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            background = Color(0xFFF0EDE8)
                        )
                    ) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}