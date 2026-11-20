package shaderx.binary

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import com.debanshu.shaderlab.shaderx.compose.shaderEffect
import com.debanshu.shaderlab.shaderx.compose.shaderEffectWithResult

// A separate frozen Compose-plugin fixture invokes both actual 0.1.3 JVM symbols.
// Null effect validates ABI/composition, without claiming UI pixels or shader execution.
@Composable
fun frozenComposeConsumer(): Modifier = Modifier
    .shaderEffect(effect = null, factory = FrozenDirectFactory(), knownSize = IntSize(32, 24))
    .shaderEffectWithResult(effect = null, factory = FrozenBaseFactory(), knownSize = IntSize(32, 24))
