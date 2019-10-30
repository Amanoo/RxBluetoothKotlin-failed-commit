package com.vincentmasselis.rxbluetoothkotlin

import android.bluetooth.*
import android.content.Context
import android.os.Build
import com.vincentmasselis.rxbluetoothkotlin.decorator.CallbackLogger
import com.vincentmasselis.rxbluetoothkotlin.internal.ContextHolder
import com.vincentmasselis.rxbluetoothkotlin.internal.hasPermissions
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers

private const val TAG = "BluetoothDevice+rx"

/**
 * Initialize a connection and returns immediately an instance of [BluetoothGatt]. It doesn't wait
 * for the connection to be established to emit a [BluetoothGatt] instance. To do this you have to
 * listen the [io.reactivex.MaybeObserver.onSuccess] event from the [Maybe] returned by
 * [whenConnectionIsReady] method.
 *
 * @param logger Set a [logger] to log every event which occurs from the BLE API (connections, writes, notifications, MTU, missing permissions, etc...).
 * @param rxGattBuilder Defaults uses a [RxBluetoothGattImpl] instance but you can fill you own. It can be useful if you want to add some business logic between the default
 * [RxBluetoothGatt] implementation and the system.
 * @param connectGattWrapper Default calls [BluetoothDevice.connectGatt]. If you want to use an other variant of [BluetoothDevice.connectGatt] regarding to your requirements,
 * replace the default implementation by your own.
 * @param rxCallbackBuilder Defaults uses a [RxBluetoothGattCallbackImpl] instance but you can fill you own. It can be useful if you want to add some business logic between the
 * default [RxBluetoothGatt.Callback] implementation and the system.
 *
 * @return
 * onSuccess with a [BluetoothGatt] when a [BluetoothGatt] instance is returned by the system API.
 *
 * onError with [NeedLocationPermission], [BluetoothIsTurnedOff] or [NullBluetoothGatt]
 *
 * @see BluetoothGattCallback
 * @see BluetoothDevice.connectGatt
 */
@Suppress("UNCHECKED_CAST")
fun <T : RxBluetoothGatt.Callback, E : RxBluetoothGatt> BluetoothDevice.connectTypedRxGatt(
    logger: Logger? = null,
    rxCallbackBuilder: () -> T = {
        RxBluetoothGattCallbackImpl().let { concrete -> logger?.let { CallbackLogger(it, concrete) } ?: concrete } as T
    },
    connectGattWrapper: (Context, BluetoothGattCallback) -> BluetoothGatt? = { context, callback ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        else connectGatt(context, false, callback)
    },
    rxGattBuilder: (BluetoothGatt, T) -> E = { gatt, callbacks ->
        RxBluetoothGattImpl(logger, gatt, callbacks) as E
    }
): Single<E> = Single
    .fromCallable {

        if (hasPermissions().not()) {
            logger?.v(TAG, "BLE require ACCESS_FINE_LOCATION permission")
            throw NeedLocationPermission()
        }

        val btState =
            if ((ContextHolder.context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter.isEnabled)
                BluetoothAdapter.STATE_ON
            else
                BluetoothAdapter.STATE_OFF

        if (btState == BluetoothAdapter.STATE_OFF) {
            logger?.v(TAG, "Bluetooth is off")
            throw BluetoothIsTurnedOff()
        }

        val callbacks = rxCallbackBuilder()

        val gatt = connectGattWrapper(ContextHolder.context, callbacks.source)

        if (gatt == null) {
            logger?.v(TAG, "connectGatt method returned null")
            throw NullBluetoothGatt()
        }

        return@fromCallable rxGattBuilder(gatt, callbacks)
    }
    .subscribeOn(AndroidSchedulers.mainThread())

/** @see connectTypedRxGatt */
fun BluetoothDevice.connectRxGatt(
    logger: Logger? = null,
    rxCallbackBuilder: (() -> RxBluetoothGatt.Callback) = {
        RxBluetoothGattCallbackImpl().let { concrete -> logger?.let { CallbackLogger(it, concrete) } ?: concrete }
    },
    connectGattWrapper: (Context, BluetoothGattCallback) -> BluetoothGatt? = { context, callback ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        else connectGatt(context, false, callback)
    },
    rxGattBuilder: ((BluetoothGatt, RxBluetoothGatt.Callback) -> RxBluetoothGatt) = { gatt, callbacks ->
        RxBluetoothGattImpl(logger, gatt, callbacks)
    }
) =
    connectTypedRxGatt(
        logger,
        rxCallbackBuilder,
        connectGattWrapper,
        rxGattBuilder
    )