package com.jlindemann.science.model

class SearchModel {

    var element: String? = null


    fun getElements(): String {
        return element.toString()
    }

    fun setElements(name: String) {
        this.element = name
    }
}