package fr.coppernic.interactors.paragon

import fr.coppernic.sdk.ask.RfidTag
import io.reactivex.Completable
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotEqual
import org.junit.Test

class WithCardTest: TestBase() {
    @Test
    fun testStartDiscovery() {
        paragonInteractor.power(true)
            .andThen(Completable.defer { paragonInteractor.open() })
            .andThen(paragonInteractor.startDiscovery())
            .blockingGet()
            .communicationMode shouldNotEqual RfidTag.CommunicationMode.Unknown
    }

    @Test
    fun sendApdu() {
        val getChallengeApdu = byteArrayOf(0x00.toByte(), 0x84.toByte(), 0x00, 0x00, 0x08)

        val answer = paragonInteractor.power(true)
            .andThen(Completable.defer { paragonInteractor.open() })
            .andThen(paragonInteractor.startDiscovery())
            .map { paragonInteractor.sendApdu(getChallengeApdu) }
            .blockingGet()

        answer.size shouldEqual 10
        answer.copyOfRange(answer.size - 2, answer.size) shouldEqual byteArrayOf(0x90.toByte(), 0x00)
    }
}
