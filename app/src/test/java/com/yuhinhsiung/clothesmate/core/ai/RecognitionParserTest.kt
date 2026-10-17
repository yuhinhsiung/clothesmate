package com.yuhinhsiung.clothesmate.core.ai

import com.yuhinhsiung.clothesmate.core.model.ClothingCategory
import com.yuhinhsiung.clothesmate.core.model.Season
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** AI 响应解析（M2-03 验收：正常 JSON / 非 JSON / 空 content 容错）。 */
class RecognitionParserTest {

    @Test
    fun `plain json is parsed`() {
        val result = RecognitionParser.parse(
            """{"category":"shirt","styleTags":["通勤","简约"],"seasonTags":["spring","autumn"]}""",
        )
        assertEquals(ClothingCategory.Shirt.key, result?.categoryKey)
        assertEquals(listOf("通勤", "简约"), result?.styleTags)
        assertEquals(listOf(Season.Spring.key, Season.Autumn.key), result?.seasonKeys)
    }

    @Test
    fun `fenced json with explanation is parsed`() {
        val result = RecognitionParser.parse(
            """
            好的，识别结果如下：
            ```json
            {"category":"连衣裙","styleTags":"甜美、复古","seasonTags":["春","秋"]}
            ```
            """.trimIndent(),
        )
        assertEquals(ClothingCategory.Dress.key, result?.categoryKey)
        assertEquals(listOf("甜美", "复古"), result?.styleTags)
        assertEquals(listOf(Season.Spring.key, Season.Autumn.key), result?.seasonKeys)
    }

    @Test
    fun `english aliases are tolerated`() {
        val result = RecognitionParser.parse(
            """{"category":"t-shirt","styleTags":["casual"],"seasonTags":["summer"]}""",
        )
        assertEquals(ClothingCategory.TShirt.key, result?.categoryKey)
        assertEquals(listOf(Season.Summer.key), result?.seasonKeys)
    }

    @Test
    fun `top category falls back to other subcategory`() {
        val result = RecognitionParser.parse("""{"category":"上衣"}""")
        assertEquals(ClothingCategory.TopOther.key, result?.categoryKey)
    }

    @Test
    fun `unknown values are dropped instead of guessed`() {
        val result = RecognitionParser.parse(
            """{"category":"spacesuit","styleTags":["未来"],"seasonTags":["monsoon"]}""",
        )
        assertNull(result?.categoryKey)
        assertEquals(listOf("未来"), result?.styleTags)
        assertTrue(result!!.seasonKeys.isEmpty())
    }

    @Test
    fun `non json returns null`() {
        assertNull(RecognitionParser.parse("抱歉，我无法识别这张图片"))
        assertNull(RecognitionParser.parse(""))
        assertNull(RecognitionParser.parse(null))
    }

    @Test
    fun `content extraction supports string and array parts`() {
        val stringResponse = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"choices":[{"message":{"content":"hello"}}]}""",
        ) as kotlinx.serialization.json.JsonObject
        assertEquals("hello", RecognitionParser.extractContent(stringResponse))

        val arrayResponse = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"choices":[{"message":{"content":[{"type":"text","text":"he"},{"type":"text","text":"llo"}]}}]}""",
        ) as kotlinx.serialization.json.JsonObject
        assertEquals("hello", RecognitionParser.extractContent(arrayResponse))
    }
}
