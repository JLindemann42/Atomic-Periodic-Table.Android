package com.jlindemann.science.model

import com.jlindemann.science.R

object OrderModel {
    fun getList(order: ArrayList<Order>) {
        order.add(Order(1, "Overview"))
        order.add(Order(2, "Properties"))
        order.add(Order(3, "Temperatures"))
        order.add(Order(4, "Atomic Properties"))
        order.add(Order(5, "Electromagnetic Properties"))
        order.add(Order(6, "Thermodynamic Properties"))
        order.add(Order(7, "Nuclear Properties"))

    }
}