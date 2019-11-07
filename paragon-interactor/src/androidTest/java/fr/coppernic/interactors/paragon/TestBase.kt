package fr.coppernic.interactors.paragon

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before

abstract class TestBase {
    lateinit var paragonInteractor: ParagonInteractor

    @Before
    fun before() {
        paragonInteractor = ParagonInteractor(
            InstrumentationRegistry.getInstrumentation()
                .targetContext)

        paragonInteractor.setUp()
            .blockingGet()
    }

    @After
    fun after() {
        paragonInteractor.close()
        paragonInteractor.power(false).subscribe {  }
    }
}
