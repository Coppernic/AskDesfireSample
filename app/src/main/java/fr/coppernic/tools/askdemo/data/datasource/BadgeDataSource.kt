package fr.coppernic.tools.askdemo.data.datasource

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import fr.coppernic.sdk.ask.DesfireStatus
import fr.coppernic.sdk.ask.Reader
import fr.coppernic.sdk.ask.RfidTag
import fr.coppernic.sdk.ask.sCARD_SearchExt
import fr.coppernic.sdk.core.Defines
import fr.coppernic.sdk.power.PowerManager
import fr.coppernic.sdk.power.api.PowerListener
import fr.coppernic.sdk.power.api.peripheral.Peripheral
import fr.coppernic.sdk.power.impl.access.AccessPeripheral
import fr.coppernic.sdk.utils.core.CpcBytes
import fr.coppernic.sdk.utils.core.CpcResult
import fr.coppernic.sdk.utils.io.InstanceListener
import fr.coppernic.tools.askdemo.domain.model.AskBadge
import fr.coppernic.tools.askdemo.presentation.App
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class BadgeDataSource {

    // Variables
    var lastTag: RfidTag? = null
    private var lastScan: Long = 0
    private var _reader: Reader? = null

    fun getData(): Flow<Result<AskBadge>> = flow {
        val ctx = App.components.applicationContext
        power(ctx, true)
        prepareReader(ctx)?.also {
            openReader(it)
            resetReader(it)
            getReaderVersion(it).also { version ->
                Log.d("ASK READER", version)
            }
            while (currentCoroutineContext().isActive) {
                val result = tryToDetectACard(it)
                if (result.isSuccess && result.getOrNull()?.communicationMode == RfidTag.CommunicationMode.Iso14443A) {
                    result.getOrNull()?.let { tag ->
                        if(tag.atr?.contentEquals(lastTag?.atr) == false || System.currentTimeMillis() > lastScan + 2000) {
                            readCardData(tag, it)?.let { badge ->
                                emit(Result.success(badge))
                            } ?: run {
                                emit(Result.failure(Throwable("Error while reading the badge")))
                            }
                            lastScan = System.currentTimeMillis()
                            lastTag = tag
                        }
                    }

                } else {
                    emit(Result.failure(Throwable("No badge detected")))
                }
                delay(DELAY_BETWEEN_READS_MILLISECONDS)
            }
            closeReader(it)
        }
        power(ctx, false)
    }

    //region READ BADGE

    private fun readCardData(rfidTag: RfidTag?, reader: Reader): AskBadge? {
        var data: String? = null
        var result: Int
        val desfireStatus = DesfireStatus(DesfireStatus.Status.RCSC_DESFIRE_TIMEOUT)

        reader.cscResetCsc(false)

        // Get the uid
        val uid: ByteArray? = reader.cscMifareDesfireGetUID(desfireStatus)


        // Select the application
        result = reader.cscMifareDesfireSelectApplication(CpcBytes.parseHexStringToArray(MIFARE_AID), desfireStatus)
        if (result != fr.coppernic.sdk.ask.Defines.RCSC_Ok || desfireStatus.status != DesfireStatus.Status.RCSC_DESFIRE_OPERATION_OK) {
            return null
        }

        // Get file settings to get the commMode (PLAIN, MACED or ENCIPHERED) and size
        val fsettings = ByteArray(17)
        result = reader.cscMifareDesfireGetFileSettings(MIFARE_FILE_NO.toByte(), fsettings, desfireStatus)
        if (result != fr.coppernic.sdk.ask.Defines.RCSC_Ok || desfireStatus.status != DesfireStatus.Status.RCSC_DESFIRE_OPERATION_OK) {
            return null
        }
        val commMode = fsettings[1]
        val size = fsettings[6].toInt()
        val dataRead = ByteArray(size)

        // Authenticate on PICC
        result = reader.cscMifareDesfireAuthenticateEV1_WithoutSAM(desfireStatus, CpcBytes.parseHexStringToArray(MIFARE_PICC_KEY), MIFARE_PICC_KEY_NUM.toByte())
        if (result != fr.coppernic.sdk.ask.Defines.RCSC_Ok || desfireStatus.status != DesfireStatus.Status.RCSC_DESFIRE_OPERATION_OK) {
            return null
        }

        // Read and decode data
        val fromOffset = byteArrayOf(0x00, 0x00, 0x00)
        val dataLength = byteArrayOf(dataRead.size.toByte(), 0x00, 0x00)
        result = reader.cscMifareDesfireEV1_ReadData(
            MIFARE_FILE_NO.toByte(),
            commMode,
            fromOffset,
            dataLength,
            desfireStatus,
            dataRead
        )

        data = if (result == fr.coppernic.sdk.ask.Defines.RCSC_Ok &&
            desfireStatus.status == DesfireStatus.Status.RCSC_DESFIRE_OPERATION_OK &&
            dataRead.isNotEmpty()) {
            CpcBytes.byteArrayToString(dataRead)
        } else {
            null
        }


        if (uid != null) {
            return AskBadge(CpcBytes.byteArrayToString(uid).removeWhitespaces(), data)
        } else if (rfidTag?.atr?.isNotEmpty() == true) {
            return AskBadge(getUidFromAtrAsString(rfidTag.atr).trim(), data)
        }
        return null
    }

    //endregion

    //region POWER

    @SuppressLint("CheckResult")
    private suspend fun power(context: Context, on: Boolean) = suspendCoroutine { continuation ->
        PERIPHERAL.descriptor.power(context, on)
            .subscribe({ continuation.resume(it) }, { CpcResult.RESULT.ERROR.message = "Unable to power" + if(on) "on" else "off" })
    }

    //endregion

    //region READER

    private suspend fun prepareReader(context: Context) = suspendCoroutine<Reader?> { continuation ->
        if (_reader != null) {
            continuation.resume(_reader)
        } else {
            // Get the reader instance
            Reader.getInstance(
                context,
                object : InstanceListener<Reader> {
                    override fun onCreated(p0: Reader) {
                        _reader = p0
                        continuation.resume(_reader)
                    }

                    override fun onDisposed(p0: Reader?) {
                        _reader = null
                        continuation.resume(_reader)
                    }
                }
            )
        }
    }
    private suspend fun openReader(reader: Reader) = withContext(Dispatchers.IO) {
        val result = reader.cscOpen(
            Defines.SerialDefines.ASK_READER_PORT,
            BAUDRATE,
            false
        )
        if (result != fr.coppernic.sdk.ask.Defines.RCSC_Ok) {
            throw Exception("Error opening ask reader. Result: $result")
        }
    }
    private suspend fun resetReader(reader: Reader) = withContext(Dispatchers.IO) {
        val result = reader.cscResetCsc()
        if (result != fr.coppernic.sdk.ask.Defines.RCSC_Ok) {
            throw Exception("Error resetting ask reader. Result: $result")
        }
    }
    private suspend fun getReaderVersion(reader: Reader): String = withContext(Dispatchers.IO) {
        val versionSb = StringBuilder()
        val result = reader.cscVersionCsc(versionSb)
        if (result != fr.coppernic.sdk.ask.Defines.RCSC_Ok) {
            throw Exception("Error getting ask reader version. Result: $result")
        }
        return@withContext versionSb.toString()
    }
    private suspend fun tryToDetectACard(reader: Reader) = suspendCoroutine<Result<RfidTag?>> { continuation ->
        // Specify the tag search parameters
        val search = sCARD_SearchExt().apply {
            OTH = 1
            CONT = 0
            INNO = 1
            ISOA = 1
            ISOB = 1
            MIFARE = 1
            MONO = 1
            MV4k = 1
            MV5k = 1
            TICK = 1
        }
        val searchMask = (
                fr.coppernic.sdk.ask.Defines.SEARCH_MASK_INNO or
                        fr.coppernic.sdk.ask.Defines.SEARCH_MASK_ISOA or
                        fr.coppernic.sdk.ask.Defines.SEARCH_MASK_ISOB or
                        fr.coppernic.sdk.ask.Defines.SEARCH_MASK_MIFARE or
                        fr.coppernic.sdk.ask.Defines.SEARCH_MASK_MONO or
                        fr.coppernic.sdk.ask.Defines.SEARCH_MASK_MV4K or
                        fr.coppernic.sdk.ask.Defines.SEARCH_MASK_MV5K or
                        fr.coppernic.sdk.ask.Defines.SEARCH_MASK_TICK or
                        fr.coppernic.sdk.ask.Defines.SEARCH_MASK_OTH
                )

        // Initialize the response variables
        val com = ByteArray(1)
        val atrLength = IntArray(1)
        val atr = ByteArray(256)

        // Call the reader functions to enter the hunt phase
        val ret = reader.cscSearchCardExt(search, searchMask, FORGET, TIMEOUT, com, atrLength, atr)
        if (ret != fr.coppernic.sdk.ask.Defines.RCSC_Ok) {
            continuation.resume(Result.failure(Exception("Reader hunt error: $ret")))
        }

        // Check if the timeout expired is returned or if the atr length is 0
        if (com[0].toInt() == COM_TIMEOUT_EXPIRED || atrLength[0] == 0) {
            continuation.resume(Result.failure(Exception("Timeout")))
        } else {
            // Emit the read RfidTag object and complete the emitter
            continuation.resume(Result.success(RfidTag(com[0], atr.copyOfRange(0, atrLength[0]))))
        }
    }
    private suspend fun closeReader(reader: Reader) = withContext(Dispatchers.IO) {
        reader.cscClose()
    }

    //endregion

    //region Util

    private fun getUidFromAtrAsString(atr: ByteArray): String {

        if (atr[0].compareTo(0x0) == 0) {
            return when (atr[1]) {
                0x07.toByte() -> { // Mifare Desfire
                    atr.drop(2).take(7).toByteArray().toHexString()
                }
                0x20.toByte() -> { // Mifare Desfire with RID (Random ID)
                    if (atr[2].compareTo(0x08) == 0) {
                        atr.drop(2).take(4).toByteArray().toHexString()
                    } else {
                        atr.drop(2).toByteArray().toHexString()
                    }
                }
                else -> { // Mifare Classic
                    atr.drop(2).toByteArray().toHexString()
                }
            }
        } else {
            return ""
        }
    }

    private fun ByteArray.toHexString(): String {
        return this.joinToString("") {
            String.format("%02x", it).uppercase()
        }
    }

    private fun String.removeWhitespaces() = filterNot { it.isWhitespace() }

    //endregion

    companion object {
        // Reader device
        val PERIPHERAL: Peripheral = AccessPeripheral.RFID_ASK_UCM108_GPIO

        // General settings
        const val BAUDRATE = 115200
        const val FORGET = 0x01.toByte()
        const val TIMEOUT = 0x00.toByte()
        const val COM_TIMEOUT_EXPIRED = 0x6F
        const val DELAY_BETWEEN_READS_MILLISECONDS = 200L

        // Mifare card details
        const val MIFARE_AID = "F51010"
        const val MIFARE_FILE_NO = 0x0
        const val MIFARE_PICC_KEY = "11111111111111111111111111111111"
        const val MIFARE_PICC_KEY_NUM = 0x01
    }
}