package br.ufop.campus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class WidgetLayoutValidationTest {

    private companion object {
        // Views e ViewGroups anotados com @RemoteView no Android SDK (exclui explicitamente android.widget.Space)
        val ALLOWED_REMOTE_VIEW_TAGS = setOf(
            "FrameLayout",
            "LinearLayout",
            "RelativeLayout",
            "GridLayout",
            "TextView",
            "ImageView",
            "Button",
            "ImageButton",
            "ProgressBar",
            "Chronometer",
            "TextClock",
            "AnalogClock",
            "ListView",
            "GridView",
            "StackView",
            "AdapterViewFlipper",
            "ViewFlipper",
            "CheckBox",
            "Switch",
            "RadioButton",
            "RadioGroup",
            "SeekBar",
            "DatePicker",
            "TimePicker"
        )

        val FORBIDDEN_TAGS = setOf(
            "Space",
            "android.widget.Space",
            "androidx.legacy.widget.Space"
        )
    }

    private fun resolveResDir(): File {
        val candidates = listOf(
            File("src/main/res"),
            File("app/src/main/res"),
            File("android-app/app/src/main/res"),
            File(System.getProperty("user.dir") ?: ".", "src/main/res"),
            File(System.getProperty("user.dir") ?: ".", "app/src/main/res"),
            File(System.getProperty("user.dir") ?: ".", "android-app/app/src/main/res")
        )
        return candidates.firstOrNull { it.isDirectory && File(it, "layout").isDirectory }
            ?: error("Diretório res/layout não encontrado a partir de user.dir=${System.getProperty("user.dir")}")
    }

    private fun parseXmlRoot(file: File): Element {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
        }
        val doc = factory.newDocumentBuilder().parse(file)
        return doc.documentElement
    }

    private fun collectAllTags(element: Element, into: MutableList<String>) {
        into.add(element.tagName)
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element) {
                collectAllTags(node, into)
            }
        }
    }

    @Test
    fun widgetLayoutsContainOnlyValidRemoteViewTagsAndNoSpace() {
        val layoutDir = File(resolveResDir(), "layout")
        val widgetFiles = layoutDir.listFiles { file ->
            file.isFile && file.name.startsWith("widget_") && file.name.endsWith(".xml")
        }?.sortedBy { it.name }.orEmpty()

        assertTrue(
            "Esperava encontrar layouts widget_*.xml em ${layoutDir.absolutePath}",
            widgetFiles.size >= 3
        )

        widgetFiles.forEach { xmlFile ->
            val root = parseXmlRoot(xmlFile)
            val tags = mutableListOf<String>()
            collectAllTags(root, tags)

            tags.forEach { tag ->
                assertFalse(
                    "Tag proibida <$tag> encontrada em ${xmlFile.name} (causa InflateException em RemoteViews)",
                    tag in FORBIDDEN_TAGS
                )
                assertTrue(
                    "Tag <$tag> em ${xmlFile.name} não está na allowlist de @RemoteView do widget",
                    tag in ALLOWED_REMOTE_VIEW_TAGS
                )
            }
        }
    }

    @Test
    fun widgetProviderMetadataDefinesRealPreviewLayouts() {
        val xmlDir = File(resolveResDir(), "xml")

        val standardInfo = File(xmlDir, "vida_academica_widget_info.xml")
        assertTrue("Arquivo ${standardInfo.name} deve existir", standardInfo.isFile)
        val standardRoot = parseXmlRoot(standardInfo)
        assertNotNull(standardRoot)
        assertEquals(
            "@layout/widget_vida_academica",
            standardRoot.getAttribute("android:previewLayout")
        )

        val compactInfo = File(xmlDir, "vida_academica_widget_compact_info.xml")
        assertTrue("Arquivo ${compactInfo.name} deve existir", compactInfo.isFile)
        val compactRoot = parseXmlRoot(compactInfo)
        assertNotNull(compactRoot)
        assertEquals(
            "@layout/widget_vida_academica_small",
            compactRoot.getAttribute("android:previewLayout")
        )
    }
}
