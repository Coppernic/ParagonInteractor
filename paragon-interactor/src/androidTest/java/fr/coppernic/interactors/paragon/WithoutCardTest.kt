package fr.coppernic.interactors.paragon

import io.reactivex.Completable
import org.amshove.kluent.shouldEqual
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Test

class WithoutCardTest: TestBase() {
    @Test
    fun testPowerAndOpen() {
        paragonInteractor.power(true)
            .andThen(Completable.defer{paragonInteractor.open()})
            .blockingGet()

        Assert.assertThat(paragonInteractor.firmwareVersion, CoreMatchers.containsString("GEN5XX"))
    }

    @Test
    fun testOpenNoPower() {
        val testObserver = paragonInteractor.open()
            .test()

        testObserver.assertError(Throwable::class.java)
    }

    @Test
    fun setGetEepromTest() {
        var initialValue:Byte? = 0x00
        paragonInteractor.power(true)
            .andThen(Completable.defer{paragonInteractor.open()})
            .andThen(paragonInteractor.getEeprom(0x01))
            .flatMapCompletable{
                initialValue = it
                paragonInteractor.setEeprom(0x01, 0x00)
            }
            .andThen(paragonInteractor.getEeprom(0x01))
            .blockingGet() shouldEqual 0x00

        paragonInteractor.setEeprom(0x01, initialValue!!)
            .andThen(paragonInteractor.getEeprom(0x01))
            .blockingGet() shouldEqual initialValue

    }
}