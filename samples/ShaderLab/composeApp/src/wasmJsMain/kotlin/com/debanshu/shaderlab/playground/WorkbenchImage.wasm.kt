package com.debanshu.shaderlab.playground
import androidx.compose.runtime.Composable
import kotlin.io.encoding.Base64
@Composable
actual fun rememberWorkbenchImagePicker(): (((Result<ByteArray>) -> Unit) -> Unit)? = { onResult ->
    pickWorkbenchPng { encoded, error ->
        onResult(runCatching {
            require(error.isEmpty()) { error }
            Base64.decode(encoded).also(::validateWorkbenchPng)
        })
    }
}
private fun pickWorkbenchPng(callback: (String, String) -> Unit): Unit = js("""{
    const input=document.createElement('input'); input.type='file'; input.accept='image/png';
    input.onchange=async function() {
        const file=input.files && input.files[0]; if(!file) return;
        if(file.size>1048576) {callback('', 'PNG must be at most 1 MiB');return;}
        try {
            const b=new Uint8Array(await file.arrayBuffer());
            const v=new DataView(b.buffer);
            if(b.length<33 || v.getUint32(16)>256 || v.getUint32(20)>256) {callback('', 'PNG dimensions must be 1–256 pixels');return;}
            let s='';for(const x of b)s+=String.fromCharCode(x);
            callback(btoa(s),'');
        } catch(e) {callback('', 'Could not read PNG');}
    }; input.click();
}""")
