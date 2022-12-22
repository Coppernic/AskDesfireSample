package fr.coppernic.tools.askdemo.data.datasource;

import android.annotation.SuppressLint;
import android.content.Context;
import java.util.Arrays;
import fr.coppernic.sdk.ask.DesfireStatus;
import fr.coppernic.sdk.ask.Reader;
import fr.coppernic.sdk.ask.ReaderListener;
import fr.coppernic.sdk.ask.RfidTag;
import fr.coppernic.sdk.ask.SearchParameters;
import fr.coppernic.sdk.ask.crypto.Utils;
import fr.coppernic.sdk.ask.sCARD_SearchExt;
import fr.coppernic.sdk.core.Defines;
import fr.coppernic.sdk.power.api.peripheral.Peripheral;
import fr.coppernic.sdk.power.impl.access.AccessPeripheral;
import fr.coppernic.sdk.utils.core.CpcBytes;
import fr.coppernic.sdk.utils.io.InstanceListener;
import timber.log.Timber;

interface ResultListener {
    void onResult(Object result, Throwable e);
}

/**
 * This is an example of pure java implementation to read a Desfire card without kotlin, RxJava or Coroutines.
 * Use for instance CardReader().scan(applicationContext) to read a card once.
 */
public class CardReader {

    // Card details
    private static final int BAUDRATE = 115200;
    private static final byte[] MIFARE_AID = new byte[]{(byte)0xF5, (byte)0x40, (byte)0x90};
    private static final byte MIFARE_FILE_NO = (byte) 0x0;
    private static final String MIFARE_PICC_KEY = "22222222222222222222222222222222";
    private static final byte MIFARE_PICC_KEY_NUM = (byte)0x01;

    // Peripheral
    public Peripheral PERIPHERAL = AccessPeripheral.RFID_ASK_UCM108_GPIO;
    public Reader reader = null;

    public void scan(Context context) {
        power(context, true, new ResultListener() {
            @Override
            public void onResult(Object result, Throwable e) {
                if (e == null) {
                    prepareReader(context, new ResultListener() {
                        @Override
                        public void onResult(Object result, Throwable e) {
                            if (result instanceof Reader) {
                                reader = (Reader) result;
                                if (openReader(reader)) {
                                    Timber.d("opened");

                                    if (resetReader(reader)) {
                                        Timber.d("reseted");
                                    }

                                    String version = getReaderVersion(reader);
                                    Timber.d("Reader version %s", version);

                                    tryToDetectACard(reader, new ResultListener() {
                                        @Override
                                        public void onResult(Object cardObj, Throwable e) {
                                            if (cardObj instanceof RfidTag) {
                                                RfidTag card = (RfidTag) cardObj;
                                                Timber.d("Card ATR %s", getUidFromAtrAsString(card.getAtr()));

                                                // Select application with APDU
                                                byte[] buffer = new byte[32];
                                                byte[] aid = MIFARE_AID;
                                                DesfireStatus status = new DesfireStatus(DesfireStatus.Status.RCSC_DESFIRE_TIMEOUT);
                                                reader.cscISOCommand(new byte[]{ (byte)0x90, (byte)0x5A, (byte)0x00, (byte)0x00, (byte)0x03, aid[2], aid[1], aid[0], 0x00}, 9, buffer, new int[32]);
                                                if (Utils.checkDesfireStatus(buffer, new byte[]{ (byte)0x91, (byte)0x00 }, 1, status)) {
                                                    Timber.tag("BadgeDataSource").d("Application selected");
                                                }

                                                // Get file settings
                                                byte[] fsettings = new byte[17];
                                                int result = reader.cscMifareDesfireGetFileSettings(MIFARE_FILE_NO, fsettings, status);
                                                if (result != fr.coppernic.sdk.ask.Defines.RCSC_Ok || status.getStatus() != DesfireStatus.Status.RCSC_DESFIRE_OPERATION_OK) {
                                                    Timber.tag("BadgeDataSource").e("Could not retrieve file settings");
                                                    return;
                                                }
                                                byte commMode = fsettings[1];
                                                int size = fsettings[6];
                                                byte[] dataRead = new byte[size];

                                                // Authenticate on PICC
                                                result = reader.cscMifareDesfireAuthenticateEV1_WithoutSAM(status, CpcBytes.parseHexStringToArray(MIFARE_PICC_KEY), MIFARE_PICC_KEY_NUM);
                                                if (result != fr.coppernic.sdk.ask.Defines.RCSC_Ok || status.getStatus() != DesfireStatus.Status.RCSC_DESFIRE_OPERATION_OK) {
                                                    Timber.tag("BadgeDataSource").e("Could not authenticate");
                                                    return;
                                                } else {
                                                    Timber.tag("BadgeDataSource").i("Authenticated !");
                                                }

                                                byte[] fromOffset = new byte[]{ (byte)0x00, (byte)0x00, (byte)0x00 };
                                                byte[] dataLength = new byte[]{ (byte)dataRead.length, (byte)0x00, (byte)0x00};
                                                result = reader.cscMifareDesfireEV1_ReadData(
                                                        MIFARE_FILE_NO,
                                                        commMode,
                                                        fromOffset,
                                                        dataLength,
                                                        status,
                                                        dataRead
                                                );
                                                if (result != fr.coppernic.sdk.ask.Defines.RCSC_Ok || status.getStatus() != DesfireStatus.Status.RCSC_DESFIRE_OPERATION_OK) {
                                                    Timber.tag("BadgeDataSource").e("Could not read data");
                                                    return;
                                                } else {
                                                    Timber.tag("BadgeDataSource").i("data %s", CpcBytes.byteArrayToString(dataRead));
                                                }

                                                closeReader(reader);
                                            }
                                        }
                                    });
                                }
                            }
                        }
                    });
                }
            }
        });
    }

    @SuppressLint("CheckResult")
    public void power(Context context, boolean on, ResultListener listener) {
        PERIPHERAL.getDescriptor().power(context, on).subscribe(listener::onResult);
    }

    public void prepareReader(Context context, ResultListener listener) {
        Reader.getInstance(
                context,
                new InstanceListener<Reader>() {
                    @Override
                    public void onCreated(Reader reader) {
                        listener.onResult(reader, null);
                    }
                    @Override
                    public void onDisposed(Reader reader) {

                    }
                }
        );
    }

    public boolean openReader(Reader reader) {
        return reader.cscOpen(
                Defines.SerialDefines.ASK_READER_PORT,
                BAUDRATE,
                false
        ) == fr.coppernic.sdk.ask.Defines.RCSC_Ok;
    }

    public boolean resetReader(Reader reader) {
        int result = reader.cscResetCsc();
        return (result != fr.coppernic.sdk.ask.Defines.RCSC_Ok);
    }


    private String getReaderVersion(Reader reader) {
        StringBuilder versionSb = new StringBuilder();
        int result = reader.cscVersionCsc(versionSb);
        if (result != fr.coppernic.sdk.ask.Defines.RCSC_Ok) {
            return null;
        }
        return versionSb.toString();
    }

    private void tryToDetectACard(Reader reader, ResultListener listener) {

        // Configure Hunt Phase
        reader.cscEnterHuntPhaseParameters(
                (byte) 0x01,
                (byte) 0x00,
                (byte) 0x00,
                (byte) 0x00,
                (byte) 0x01,
                (byte) 0x00,
                (byte) 0x00,
                (byte) 0x00,
                null,
                (byte) 0x00,
                (byte) 0x00
        );

        // Specify the tag search parameters
        sCARD_SearchExt search = new sCARD_SearchExt();
        search.OTH = 1;
        search.CONT = 0;
        search.INNO = 1;
        search.ISOA = 1;
        search.ISOB = 1;
        search.MIFARE = 1 ; // warning : should be 0 for RandomId card to enable ISO level 4 features in ParagonId stack
        search.MONO = 1;
        search.MV4k = 1;
        search.MV5k = 1;
        search.TICK = 1;

        int mask = fr.coppernic.sdk.ask.Defines.SEARCH_MASK_INNO | fr.coppernic.sdk.ask.Defines.SEARCH_MASK_ISOA | fr.coppernic.sdk.ask.Defines.SEARCH_MASK_ISOB | fr.coppernic.sdk.ask.Defines.SEARCH_MASK_MIFARE | fr.coppernic.sdk.ask.Defines.SEARCH_MASK_MONO | fr.coppernic.sdk.ask.Defines.SEARCH_MASK_MV4K | fr.coppernic.sdk.ask.Defines.SEARCH_MASK_MV5K | fr.coppernic.sdk.ask.Defines.SEARCH_MASK_TICK | fr.coppernic.sdk.ask.Defines.SEARCH_MASK_OTH;
        SearchParameters parameters = new SearchParameters(search, mask, (byte) 0x01, (byte) 0xFF);
        // Starts card detection
        reader.startDiscovery(parameters, new ReaderListener() {
            @Override
            public void onTagDiscovered(RfidTag rfidTag) {
                listener.onResult(rfidTag, null);
            }

            @Override
            public void onDiscoveryStopped() {

            }
        });
    }

    private void closeReader(Reader reader) {
        reader.cscClose();
    }

    public static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for(byte b: a)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private String getUidFromAtrAsString(byte[] atr) {
        if (atr != null && atr.length >= 11) {
            byte[] slice = Arrays.copyOfRange(atr, 2, 9);
            return byteArrayToHex(slice);
        }
        return "";
    }
}
