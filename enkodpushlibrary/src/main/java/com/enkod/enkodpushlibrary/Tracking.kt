package com.enkod.enkodpushlibrary

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.UUID

object Tracking {

    private var onProductActionCallback: (String) -> Unit = {}
    private var onErrorCallback: (String) -> Unit = {}


    private fun createHistoryJsonForCartAndFavourite (product: Product): JsonObject {

        val history = JsonObject()

        if (product.id != null) {
            history.addProperty("productId", product.id!!)
        }
        if (product.categoryId != null) {
            history.addProperty("categoryId", product.categoryId!!)
        }
        if (product.count != null) {
            history.addProperty("count", product.count!!)
        }
        if (product.price != null) {
            history.addProperty("price", product.price!!)
        }
        if (product.picture != null) {
            history.addProperty("picture", product.picture!!)
        }

        if (product.params != null) {

            val paramMap = product.params

            for (key in paramMap!!.keys) {

                val value = paramMap[key]

                try {

                    when (value) {

                        is String -> history.addProperty(key, value)
                        is Int -> history.addProperty(key, value)
                        is Boolean -> history.addProperty(key, value)
                        is Float -> history.addProperty(key, value)
                        is Double -> history.addProperty(key, value)
                        else -> history.addProperty(key, value.toString())

                    }
                } catch (e: Exception) {
                    EnkodPushLibrary.logInfo("error createMapHistory $e")
                }
            }
        }

        if (history.isJsonNull) {
            history.addProperty("", "")
        }

        return history
    }



    fun addToCart(product: Product) {

        EnkodPushLibrary.initLibObserver.observable.subscribe {

            if (it) {

                if (!product.id.isNullOrEmpty()) {

                    val products = JsonObject()
                    val history = createHistoryJsonForCartAndFavourite(product)

                    val property = "cart"

                    products.addProperty("productId", product.id)
                    products.addProperty("count", product.count)

                    history.addProperty("action", "productAdd")

                    val req = JsonObject().apply {
                        add(property, JsonObject()
                            .apply {
                                addProperty("lastUpdate", System.currentTimeMillis())
                                add("products", JsonArray().apply { add(products) })
                            })
                        add("history", JsonArray().apply { add(history) })
                    }

                    Log.d("req", req.toString())

                    EnkodPushLibrary.retrofit.addToCart(
                        EnkodPushLibrary.getClientName(),
                        EnkodPushLibrary.getSession(),
                        req
                    ).enqueue(object : Callback<Unit> {
                        override fun onResponse(
                            call: Call<Unit>,
                            response: Response<Unit>
                        ) {
                            val msg = "add to cart success"
                            EnkodPushLibrary.logInfo(msg)
                            onProductActionCallback(msg)

                        }

                        override fun onFailure(call: Call<Unit>, t: Throwable) {
                            val msg = "error when adding product to cart: ${t.localizedMessage}"
                            EnkodPushLibrary.logInfo(msg)
                            onProductActionCallback(msg)
                            onErrorCallback(msg)
                        }
                    })
                } else return@subscribe
            }
        }
    }

    fun removeFromCart(product: Product) {

        EnkodPushLibrary.initLibObserver.observable.subscribe {

            if (it) {

                if (!product.id.isNullOrEmpty()) {

                    val products = JsonObject()
                    val history = createHistoryJsonForCartAndFavourite(product)

                    val property = "cart"

                    products.addProperty("productId", product.id)
                    products.addProperty("count", product.count)

                    history.addProperty("action", "productRemove")

                    val req = JsonObject().apply {
                        add(property, JsonObject()
                            .apply {
                                addProperty("lastUpdate", System.currentTimeMillis())
                                add("products", JsonArray().apply { add(products) })
                            })
                        add("history", JsonArray().apply { add(history) })
                    }

                    Log.d("req", req.toString())

                    EnkodPushLibrary.retrofit.addToCart(
                        EnkodPushLibrary.getClientName(),
                        EnkodPushLibrary.getSession(),
                        req
                    ).enqueue(object : Callback<Unit> {
                        override fun onResponse(
                            call: Call<Unit>,
                            response: Response<Unit>
                        ) {
                            val msg = "remove from cart success"
                            EnkodPushLibrary.logInfo(msg)
                            onProductActionCallback(msg)

                        }

                        override fun onFailure(call: Call<Unit>, t: Throwable) {
                            val msg = "error when adding product to cart: ${t.localizedMessage}"
                            EnkodPushLibrary.logInfo(msg)
                            onProductActionCallback(msg)
                            onErrorCallback(msg)
                        }
                    })
                } else return@subscribe
            }
        }
    }

    fun addToFavourite(product: Product) {

        EnkodPushLibrary.initLibObserver.observable.subscribe {

            if (it) {

                if (!product.id.isNullOrEmpty()) {

                    val products = JsonObject()
                    val history = createHistoryJsonForCartAndFavourite(product)

                    val property = "wishlist"

                    products.addProperty("productId", product.id)
                    products.addProperty("count", product.count)

                    history.addProperty("action", "productLike")

                    val req = JsonObject().apply {
                        add(property, JsonObject()
                            .apply {
                                addProperty("lastUpdate", System.currentTimeMillis())

                                add("products", JsonArray().apply { add(products) })
                            })
                        add("history", JsonArray().apply { add(history) })
                    }

                    Log.d("req", req.toString())


                    EnkodPushLibrary.retrofit.addToFavourite(
                        EnkodPushLibrary.getClientName(),
                        EnkodPushLibrary.getSession(),
                        req
                    ).enqueue(object : Callback<Unit> {
                        override fun onResponse(
                            call: Call<Unit>,
                            response: Response<Unit>
                        ) {

                            val msg = "add to favourite success"
                            EnkodPushLibrary.logInfo(msg)
                            onProductActionCallback(msg)

                        }

                        override fun onFailure(call: Call<Unit>, t: Throwable) {
                            Log.d("Favourite", "${t.localizedMessage}")
                            val msg = "error when adding product to cart: ${t.localizedMessage}"
                            EnkodPushLibrary.logInfo(msg)
                            onProductActionCallback(msg)
                            onErrorCallback(msg)
                        }
                    })

                } else return@subscribe
            }
        }
    }

    fun removeFromFavourite(product: Product) {

        EnkodPushLibrary.initLibObserver.observable.subscribe {

            if (it) {

                if (!product.id.isNullOrEmpty()) {


                    val products = JsonObject()
                    val history = createHistoryJsonForCartAndFavourite(product)

                    val property = "wishlist"

                    products.addProperty("productId", product.id)
                    products.addProperty("count", product.count)

                    history.addProperty("action", "productDislike")

                    val req = JsonObject().apply {
                        add(property, JsonObject()
                            .apply {
                                addProperty("lastUpdate", System.currentTimeMillis())
                                add("products", JsonArray().apply { add(products) })
                            })
                        add("history", JsonArray().apply { add(history) })
                    }

                    Log.d("req", req.toString())


                    EnkodPushLibrary.retrofit.addToFavourite(
                        EnkodPushLibrary.getClientName(),
                        EnkodPushLibrary.getSession(),
                        req
                    ).enqueue(object : Callback<Unit> {
                        override fun onResponse(
                            call: Call<Unit>,
                            response: Response<Unit>
                        ) {
                            val msg = "remove from favourite success"
                            EnkodPushLibrary.logInfo(msg)
                            onProductActionCallback(msg)

                        }

                        override fun onFailure(call: Call<Unit>, t: Throwable) {
                            val msg = "error when adding product to cart: ${t.localizedMessage}"
                            EnkodPushLibrary.logInfo(msg)
                            onProductActionCallback(msg)
                            onErrorCallback(msg)
                        }
                    })

                } else return@subscribe
            }
        }
    }

    fun productBuy(order: Order) {

        EnkodPushLibrary.initLibObserver.observable.subscribe {

            if (it) {

                if (order.id.isNullOrEmpty()) {
                    order.id = UUID.randomUUID().toString()
                }

                val orderInfo = JsonObject()
                val items = JsonArray()

                val position = JsonObject()
                position.addProperty("productId", order.productId)

                items.add(position)

                orderInfo.add("items", items)

                orderInfo.add("order", JsonObject().apply {
                    if (order.sum != null) {
                        addProperty("sum", order.sum)
                    }
                    if (order.price != null) {
                        addProperty("price", order.price)
                    }
                    if (order.productId != null) {
                        addProperty("productId", order.productId)
                    }
                    if (order.count != null) {
                        addProperty("count", order.count)
                    }
                    if (order.picture != null) {
                        addProperty("picture", order.picture)
                    }

                    if (order.params != null) {
                        val paramMap = order.params

                        for (key in paramMap!!.keys) {

                            val value = paramMap[key]

                            try {

                                when (value) {

                                    is String -> addProperty(key, value)
                                    is Int -> addProperty(key, value)
                                    is Boolean -> addProperty(key, value)
                                    is Float -> addProperty(key, value)
                                    is Double -> addProperty(key, value)
                                    else -> addProperty(key, value.toString())

                                }
                            } catch (e: Exception) {
                                EnkodPushLibrary.logInfo("error createMapHistory $e")
                            }
                        }
                    }
                })

                val req = JsonObject().apply {
                    addProperty("orderId", order.id)
                    add("orderInfo", orderInfo)
                }
                Log.d("buy", req.toString())
                EnkodPushLibrary.retrofit.order(
                    EnkodPushLibrary.getClientName(),
                    EnkodPushLibrary.getSession(),
                    req
                ).enqueue(object : Callback<Unit> {
                    override fun onResponse(call: Call<Unit>, response: Response<Unit>) {
                        val msg = "buying ok"
                        EnkodPushLibrary.logInfo(msg)
                        onProductActionCallback(msg)
                    }

                    override fun onFailure(call: Call<Unit>, t: Throwable) {
                        val msg = "error when buying: ${t.localizedMessage}"
                        EnkodPushLibrary.logInfo(msg)
                        onProductActionCallback(msg)
                        onErrorCallback(msg)
                    }
                })
            }
        }
    }


    fun productOpen(product: Product) {
        EnkodPushLibrary.initLibObserver.observable.subscribe {

            if (it) {

                if (!product.id.isNullOrEmpty()) {

                    val params = JsonObject()

                    params.addProperty("categoryId", product.categoryId)
                    params.addProperty("price", product.price)
                    params.addProperty("picture", product.picture)


                    val productRequest = JsonObject().apply {
                        addProperty("id", product.id)
                        add("params", params)
                    }
                    val req = JsonObject().apply {
                        addProperty("action", "productOpen")
                        add("product", productRequest)
                    }

                    Log.d("open", req.toString())

                    EnkodPushLibrary.retrofit.productOpen(
                        EnkodPushLibrary.getClientName(),
                        EnkodPushLibrary.getSession(),
                        req

                    ).enqueue(object : Callback<Unit> {
                        override fun onResponse(call: Call<Unit>, response: Response<Unit>) {
                            val msg = "product opened"
                            EnkodPushLibrary.logInfo(msg)
                            onProductActionCallback(msg)
                        }

                        override fun onFailure(call: Call<Unit>, t: Throwable) {
                            val msg = "error when saving product open: ${t.localizedMessage}"
                            EnkodPushLibrary.logInfo(msg)
                            onProductActionCallback(msg)
                            onErrorCallback(msg)
                        }
                    })
                } else return@subscribe
            }
        }
    }

}