package com.jlindemann.science.model

import android.content.Context

object AchievementModel {
    fun getList(context: Context, achievements: ArrayList<Achievement>) {
        //Mathematical:
        val achievement1 = Achievement(1, "It's a start!", "Open information about elements 10 times", 0, 10)
        achievement1.loadProgress(context)
        achievements.add(achievement1)

        val achievement2 = Achievement(2, "Chemical Junior Certified!", "Open information about elements 100 times", 0, 100)
        achievement2.loadProgress(context)
        achievements.add(achievement2)

        val achievement3= Achievement(3, "Chemical Professional Certified!", "Open information about elements 1000 times", 0, 1000)
        achievement3.loadProgress(context)
        achievements.add(achievement3)

        val achievement4= Achievement(4, "Chemical Obsession Certified!", "Open information about elements 10000 times", 0, 10000)
        achievement4.loadProgress(context)
        achievements.add(achievement4)

        val achievement5= Achievement(5, "Master of Table Manners!", "Open different tables a 1000 times", 0, 1000)
        achievement5.loadProgress(context)
        achievements.add(achievement5)

        val achievement6= Achievement(6, "Starting to get a hang of stuff!", "Enter the dictionary 50 times", 0, 50)
        achievement6.loadProgress(context)
        achievements.add(achievement6)

        val achievement7= Achievement(7, "Element of Surprise!", "Open a random element from the random button", 0, 1)
        achievement7.loadProgress(context)
        achievements.add(achievement7)

        val achievement8= Achievement(8, "Your own search engine", "Search 1000 times in the app", 0, 1000)
        achievement8.loadProgress(context)
        achievements.add(achievement8)
    }
}