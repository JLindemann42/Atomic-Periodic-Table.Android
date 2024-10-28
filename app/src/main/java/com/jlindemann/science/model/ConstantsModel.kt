package com.jlindemann.science.model

object ConstantsModel {
    fun getList(constants: ArrayList<Constants>) {

        //Mathematical:
        constants.add(Constants("Pi (π)", 3.14159265358979323846, "-","π", "mathematics"))
        constants.add(Constants("Tau (τ)", 6.28318530717958647692, "-","τ", "mathematics"))
        constants.add(Constants("Euler's number", 2.71828182845904523536, "-", "e", "mathematics"))
        constants.add(Constants("Euler's constant", 0.57721566490153286060, "-","γ", "mathematics"))
        constants.add(Constants("Golden ratio", 1.61803398874989484820, "-","φ", "mathematics"))
        constants.add(Constants("Catalan constant", 0.57721566490153286060, "-","C", "mathematics"))
        constants.add(Constants("Square root of 2", 1.41421356237309504880, "-","√2", "mathematics"))
        constants.add(Constants("Square root of 10", 3.16227766016837950288, "-","√10", "mathematics"))


        //Physical:
        constants.add(Constants("Speed of light", 299792458.0, "ms^-1","c", "physics"))
        constants.add(Constants("Planck's constant", 6.626070040e-34, "m^2*kg*s^-1","h", "physics"))
        constants.add(Constants("Gravitational constant", 6.67408e-11, "m^3*kg^-1*s^-2","G", "physics"))
        constants.add(Constants("Avogadro's number", 6.02214076e23, "mol^-1","NA", "physics"))
        constants.add(Constants("Elementary charge", 1.602176565e-19, "C","e", "physics"))
        constants.add(Constants("Electron mass", 9.10938188e-31, "kg","me", "physics"))
        constants.add(Constants("Proton mass", 1.67262158e-27, "kg","mp", "physics"))
        constants.add(Constants("Neutron mass", 1.674927281e-27, "kg","mn", "physics"))
        constants.add(Constants("Elementary charge", 1.602176565e-19, "C","e", "physics"))
        constants.add(Constants("Atomic mass unit", 1.660538782e-27, "kg","u", "physics"))
        constants.add(Constants("Boltzmann constant", 1.38064852e-23, "J*K^-1","k", "physics"))
        constants.add(Constants("Faraday constant", 96485.3399, "C*mol^-1","F", "physics"))
        constants.add(Constants("Bohr magneton", 9.274009994e-24, "J*T^-1","μB", "physics"))
        constants.add(Constants("Bohr radius", 5.2917721067e-11, "m","a₀", "physics"))
        constants.add(Constants("Acceleration due to gravity", 9.80665, "m*s^-2","g", "physics"))
        constants.add(Constants("Vacuum permittivity", 8.854187817e-12, "F*m^-1","ε₀", "physics"))
        constants.add(Constants("Vacuum permeability", 1.2566370614e-06, "H*m^-1","μ₀", "physics"))
        constants.add(Constants("Gas constant", 8.31446261815324, "J*K^-1*mol^-1","R", "physics"))

    }
}