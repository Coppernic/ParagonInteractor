package fr.coppernic.interactors.paragon

import android.content.Context
import fr.coppernic.sdk.ask.Defines
import fr.coppernic.sdk.ask.Reader
import fr.coppernic.sdk.ask.Reader.getInstance
import fr.coppernic.sdk.ask.RfidTag
import fr.coppernic.sdk.ask.sCARD_SearchExt
import fr.coppernic.sdk.power.impl.cone.ConePeripheral
import fr.coppernic.sdk.utils.core.CpcDefinitions
import fr.coppernic.sdk.utils.io.InstanceListener
import io.reactivex.Completable
import io.reactivex.CompletableEmitter
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers

class ParagonInteractor(private val context: Context) {
    lateinit var firmwareVersion: String

    private lateinit var instanceEmitter:CompletableEmitter
    private var reader: Reader? = null

    fun setUp(): Completable {

        if (reader != null) {
            return Completable.complete()
        }

        return Completable.create {
            instanceEmitter = it

            getInstance(context, object:InstanceListener<Reader> {
                override fun onDisposed(p0: Reader) {

                }

                override fun onCreated(p0: Reader) {
                    reader = p0
                    instanceEmitter.onComplete()
                }
            })
        }
    }

    /**
     * Controls power state of the Paragon reader
     */
    fun power(on: Boolean): Completable {
        return Completable.fromSingle(ConePeripheral.RFID_ASK_UCM108_GPIO.descriptor.power(context, on))
    }

    /**
     * Opens reader
     */
    fun open(): Completable {
        reader.let{
            if (it == null) {
                return Completable.error(Throwable("reader is null"))
            }

            var res = it.cscOpen(CpcDefinitions.ASK_READER_PORT, 115200, false)

            if (res != Defines.RCSC_Ok) {
                return Completable.error(Throwable("open failed"))
            }

            val sb = StringBuilder()

            res = it.cscVersionCsc(sb)

            return if (res != Defines.RCSC_Ok || !sb.toString().contains("GEN5XX")) {
                Completable.error(Throwable("get version failed"))
            } else {
                firmwareVersion = sb.toString()
                Completable.complete()
            }
        }
    }

    /**
     * Closes reader
     */
    fun close() {
        reader.let{
            it?.cscClose()
        }
    }

    /**
     * Gets list of Gain A possible values
     */
    fun getGainAValues(): List<String> {
        val dbValues = mutableListOf<String>()

        for (t in Defines.CscMifareNxpGain.values()) {
            dbValues.add(t.toString())
        }

        return dbValues
    }

    /**
     * Sets Gain A value
     */
    fun setGainA(value: String):Boolean {
        reader.let {
            val valueAsGain = Defines.CscMifareNxpGain.fromInt(Integer.parseInt(value))
            return it?.mifareNxpSetParam(Defines.MIFARE_EEPROM_ADDR_GAIN_A, valueAsGain.value) == Defines.RCSC_Ok
        }
    }

    private fun getEepromValue(index:Byte):Byte {
        val value = ByteArray(1)
        reader.let {
            val ret = it?.cscReadEeprom(index, value)

            if (ret != Defines.RCSC_Ok) {
                throw Throwable()
            }
        }

        return value[0]
    }

    fun getEeprom(index: Byte): Single<Byte> {
        return Single.fromCallable {
            getEepromValue(index)
        }
    }

    fun setEeprom(index: Byte, value: Byte): Completable {
        reader.let {
            val ret = it?.cscWriteEeeprom(index, value)

            return if (ret != Defines.RCSC_Ok) {
                Completable.error(Throwable())
            } else {
                Completable.complete()
            }
        }
    }

    fun startDiscovery():Single<RfidTag> {
        return Single.fromCallable {
            // 1 - Sets the enter hunt phase parameters to no select application
            val search = sCARD_SearchExt()
            search.OTH = 1
            search.CONT = 1
            search.INNO = 1
            search.ISOA = 1
            search.ISOB = 1
            search.MIFARE = 0
            search.MONO = 1
            search.MV4k = 1
            search.MV5k = 1
            search.TICK = 1
            val mask = Defines.SEARCH_MASK_ISOA or Defines.SEARCH_MASK_MIFARE or Defines.SEARCH_MASK_CONT or Defines.SEARCH_MASK_INNO or Defines.SEARCH_MASK_ISOB
            val com = ByteArray(1)
            val lpcbAtr = IntArray(1)
            val atr = ByteArray(64)

            val ret = reader!!.cscSearchCardExt(search, mask, 0x00.toByte(), 0x33.toByte(), com, lpcbAtr, atr)
            val rfidTag: RfidTag
            if (ret == Defines.RCSC_Timeout || com[0] == 0x6F.toByte() || com[0] == 0x00.toByte()) {
                //isTransmitting.unlock();
                rfidTag = RfidTag(0x6F.toByte(), ByteArray(0))
            } else {
                val correctSizedAtr = ByteArray(lpcbAtr[0])
                System.arraycopy(atr, 0, correctSizedAtr, 0, correctSizedAtr.size)
                rfidTag = RfidTag(com[0], correctSizedAtr)
            }

            rfidTag
        }.subscribeOn(Schedulers.io())
    }

    fun sendApdu(apdu:ByteArray):ByteArray {

        val answer = ByteArray(260)
        val answerLength = IntArray(1)
        val ret = reader!!.cscISOCommand(apdu, apdu.size, answer, answerLength)

        if (ret == Defines.RCSC_Ok) {
            val retVal = answer.copyOfRange(1, answerLength[0]);
            return retVal
        }

        return ByteArray(0)
    }
}