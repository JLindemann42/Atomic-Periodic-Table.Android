package com.jlindemann.science.model

object GeologyModel {
    fun getList(geology: ArrayList<Geology>) {

        //Rocks:
        geology.add(Geology("Andesite", 0.20, 0.35, "rock"))
    }
}