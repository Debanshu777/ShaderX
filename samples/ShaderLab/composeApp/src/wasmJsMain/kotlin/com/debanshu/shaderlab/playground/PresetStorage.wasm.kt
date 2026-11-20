@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.debanshu.shaderlab.playground

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberPresetStorage(slot: String): PresetStorage = remember(slot) {
    val limit = storageLimit(slot)
    object : PresetStorage {
        override val supportsFiles = true
        override fun load(): String? = localRead(slot, limit)
        override fun save(text: String) { require(text.encodeToByteArray().size <= limit); localWrite(slot, text) }
        override fun exportFile(text: String) { require(text.encodeToByteArray().size <= limit); download(slot, text) }
        override fun importFile(onResult: (Result<String>) -> Unit) = pick(limit) { text, error ->
            onResult(if (error.isEmpty()) Result.success(text) else Result.failure(IllegalArgumentException(error)))
        }
    }
}
private fun localRead(slot: String, limit: Int): String? = js("{ const text=localStorage.getItem('shaderlab.'+slot+'.v1'); if(text && text.length>limit) throw new Error('Local file exceeds byte limit'); return text; }")
private fun localWrite(slot: String, text: String): Unit = js("{ localStorage.setItem('shaderlab.'+slot+'.v1', text); }")
private fun download(slot: String, text: String): Unit = js("""{
    const url = URL.createObjectURL(new Blob([text], {type:'text/plain;charset=utf-8'}));
    const a = document.createElement('a'); a.href=url; a.download=slot==='workbench'?'shaderlab-draft.sxw':'shaderlab.sxp'; a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}""")
private fun pick(limit: Int, callback: (String, String) -> Unit): Unit = js("""{
    const input=document.createElement('input'); input.type='file'; input.accept='.sxp,.sxw,text/plain';
    input.onchange=async function() {
        const file=input.files && input.files[0]; if(!file) return;
        if(file.size>limit) { callback('', 'File exceeds local slot byte limit'); return; }
        try { callback(await file.text(), ''); } catch(e) { callback('', 'Could not read preset'); }
    }; input.click();
}""")
