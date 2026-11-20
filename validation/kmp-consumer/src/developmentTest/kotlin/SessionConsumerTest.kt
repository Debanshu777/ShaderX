package shaderx.consumer

import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.create
import kotlin.test.Test
import kotlin.test.assertNotNull

class SessionConsumerTest {
    @Test
    fun publicSessionAcceptsExternalAnimatedEffect() {
        val factory = ConsumerFactory(ShaderFactory.create())
        try {
            val animated = AnimatedEffect()
            val session = assertNotNull(factory.createAnimatedSession(animated, 16f, 16f).getOrNull())
            try {
                assertNotNull(session.refresh(animated.withTime(1f), 16f, 16f).getOrNull())
            } finally {
                session.close()
            }
        } finally {
            factory.close()
        }
    }
}
