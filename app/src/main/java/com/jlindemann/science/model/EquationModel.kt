package com.jlindemann.science.model

import com.jlindemann.science.R

object EquationModel {
    fun getList(equation: ArrayList<Equation>) {
        equation.add(Equation("Density", "Physics", R.drawable.e_density, "p: Density \nm: Mass \nV: Volume"))
        equation.add(Equation("Power/ Effect", "Physics", R.drawable.e_powereffect, "P: Power/ Effect \nE: Energy \nt: Time"))
        equation.add(Equation("Intensity", "Physics", R.drawable.e_intensity, "I: Intensity \nP: Power/ Effect \nA: Area"))
        equation.add(Equation("Efficiency", "Physics", R.drawable.e_efficiency, "n: Energy conversion efficiency \nE(in): Energy (in) \nE(out): Energy (out) \n" + "P(in): Power (in) \n" + "P(out): Power (out)"))
        equation.add(Equation("Frequency", "Physics", R.drawable.e_frequency, "f: Frequency \nT: Period"))
        equation.add(Equation("Angular Velocity", "Physics", R.drawable.e_angularvelocity, "w: Angular Velocity \nf: Frequency \nT: Period"))
        equation.add(Equation("Average Velocity", "Physics", R.drawable.e_averagevelocity, "v(average): Average Velocity \ns: End Position \ns(0): Start Position \nt: Time"))
        equation.add(Equation("Linear Motion", "Physics", R.drawable.e_linearmotion, "s: Position Change \nv: Velocity \nt: Time"))
        equation.add(Equation("Average Acceleration", "Physics", R.drawable.e_averageacceleration, "a(average): Average Acceleration \nv: End Velocity \nv(0): Start Velocity \nt: Time"))
        equation.add(Equation("Instantaneous Velocity #1", "Physics", R.drawable.e_instantvelocity_1, "v: End Velocity \nv(0): Start Velocity \na: Acceleration \nt: Time"))
        equation.add(Equation("Instantaneous Velocity #2", "Physics", R.drawable.e_instantvelocity_2, "s: Position Change \nv(0): Start Velocity \na: Acceleration \nt: Time"))
        equation.add(Equation("Instantaneous Velocity #3", "Physics", R.drawable.e_instantvelocity_3, "v: End Velocity \nv(0): Start Velocity \na: Acceleration \ns: Position Change"))
        equation.add(Equation("Instantaneous Velocity #4", "Physics", R.drawable.e_instantvelocity_4, "v(average): Average Velocity \nv(0): Start Velocity \nv: End Acceleration"))
        equation.add(Equation("Newtown’s Second Law (Force Law)", "Physics", R.drawable.e_forcelaw, "F: Force \nm: Mass \na: Acceleration"))
        equation.add(Equation("Friction", "Physics", R.drawable.e_friction, "F(friction): Friction Force \nF(N): Normal Force \nu: Friction Coefficient"))
        equation.add(Equation("Hookes Law", "Physics", R.drawable.e_hookeslaw, "F(spring): Spring Force \nk: Spring Coefficient \ndelta l: Spring Extension"))
        equation.add(Equation("Law of Gravity", "Physics", R.drawable.e_lawofgravity, "F(g): Gravitational Force \nG: Gravitation Coefficient \nm: Mass \nr: Radius"))
        equation.add(Equation("Force Moment", "Physics", R.drawable.e_moment, "M: Force Moment \nF: Force \nt: Time"))
        equation.add(Equation("Impulse", "Physics", R.drawable.e_impulse, "I: Impulse \nF: Force \nt: Time"))
        equation.add(Equation("Law of Impulse", "Physics", R.drawable.e_lawofimpulse, "F: Force \nt: Time \nn: Mass \nv: End Velocity \nv(0) Start Velocity"))
        equation.add(Equation("Momentum", "Physics", R.drawable.e_momentum, "p: Momentum \nm: Mass \nv: Velocity"))
        equation.add(Equation("Conservation of Momentum", "Physics", R.drawable.e_lawofmomentum, "u: Velocity before \nv: Velocity after \nm: Mass"))
    }
}