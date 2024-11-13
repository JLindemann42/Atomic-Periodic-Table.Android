package com.jlindemann.science.model

object ConstantsModel {
    fun getList(constants: ArrayList<Constants>) {

        //Mathematical:
        constants.add(Constants("Pi (π)", "3.14159265358979323846", "-","π", "mathematics"))
        constants.add(Constants("Tau (τ)", "6.28318530717958647692", "-","τ", "mathematics"))
        constants.add(Constants("Euler's number", "2.71828182845904523536", "-", "e", "mathematics"))
        constants.add(Constants("Euler's constant", "0.57721566490153286060", "-","γ", "mathematics"))
        constants.add(Constants("Golden ratio", "1.61803398874989484820", "-","φ", "mathematics"))
        constants.add(Constants("Catalan constant", "0.57721566490153286060", "-","C", "mathematics"))
        constants.add(Constants("Square root of 2", "1.41421356237309504880", "-","√2", "mathematics"))
        constants.add(Constants("Square root of 10", "3.16227766016837950288", "-","√10", "mathematics"))


        //Physical:
        constants.add(Constants("Speed of light", "299792458.0", "ms^-1","c", "physics"))
        constants.add(Constants("Planck's constant", "6.626070040e-34", "m^2*kg*s^-1","h", "physics"))
        constants.add(Constants("Gravitational constant", "6.67408e-11", "m^3*kg^-1*s^-2","G", "physics"))
        constants.add(Constants("Avogadro's number", "6.02214076e23", "mol^-1","NA", "physics"))
        constants.add(Constants("Elementary charge", "1.602176565e-19", "C","e", "physics"))
        constants.add(Constants("Electron mass", "9.10938188e-31", "kg","me", "physics"))
        constants.add(Constants("Proton mass", "1.67262158e-27", "kg","mp", "physics"))
        constants.add(Constants("Neutron mass", "1.674927281e-27", "kg","mn", "physics"))
        constants.add(Constants("Elementary charge", "1.602176565e-19", "C","e", "physics"))
        constants.add(Constants("Atomic mass unit", "1.660538782e-27", "kg","u", "physics"))
        constants.add(Constants("Boltzmann constant", "1.38064852e-23", "J*K^-1","k", "physics"))
        constants.add(Constants("Faraday constant", "96485.3399", "C*mol^-1","F", "physics"))
        constants.add(Constants("Bohr magneton", "9.274009994e-24", "J*T^-1","μB", "physics"))
        constants.add(Constants("Bohr radius", "5.2917721067e-11", "m","a₀", "physics"))
        constants.add(Constants("Acceleration due to gravity", "9.80665", "m*s^-2","g", "physics"))
        constants.add(Constants("Vacuum permittivity", "8.854187817e-12", "F*m^-1","ε₀", "physics"))
        constants.add(Constants("Vacuum permeability", "1.2566370614e-06", "H*m^-1","μ₀", "physics"))
        constants.add(Constants("Gas constant", "8.31446261815324", "J*K^-1*mol^-1","R", "physics"))

        //Water:
        constants.add(Constants("Airport, Passenger", "10-20", "L/(unit*d)","Wastewater flow rate (USA)", "water"))
        constants.add(Constants("Apartment, Bedroom", "380-570", "L/(unit*d)","Wastewater flow rate (USA)", "water"))
        constants.add(Constants("Automobile service station, Vehicle", "30-60", "L/(unit*d)","Wastewater flow rate (USA)", "water"))
        constants.add(Constants("Automobile service station, Employee", "35-60", "L/(unit*d)","Wastewater flow rate (USA)", "water"))
        constants.add(Constants("Bar/ lounge, Seat", "45-95", "L/(unit*d)","Wastewater flow rate (USA)", "water"))
        constants.add(Constants("Bar/ lounge, Employee", "40-60", "L/(unit*d)","Wastewater flow rate (USA)", "water"))
        constants.add(Constants("Boarding house, Person", "95-250", "L/(unit*d)","Wastewater flow rate (USA)", "water"))
        constants.add(Constants("Conference center, Person", "40-60", "L/(unit*d)","Wastewater flow rate (USA)", "water"))
        constants.add(Constants("Department store, Restroom", "1300-2300", "L/(unit*d)","Wastewater flow rate (USA)", "water"))
        constants.add(Constants("Department store, Employee", "30-60", "L/(unit*d)","Wastewater flow rate (USA)", "water"))
        constants.add(Constants("Hotel, Guest", "150-230", "L/(unit*d)","Wastewater flow rate (USA)", "water"))
        constants.add(Constants("Hotel, Employee", "30-60", "L/(unit*d)","Wastewater flow rate (USA)", "water"))
        constants.add(Constants("Industrial building, Employee", "60-130", "L/(unit*d)","Wastewater flow rate (USA)", "water"))
        constants.add(Constants("Laundry (self-service), Machine", "1500-2100", "L/(unit*d)","Wastewater flow rate (USA)", "water"))
        constants.add(Constants("Laundry (self-service), Customer", "170-210", "L/(unit*d)","Wastewater flow rate (USA)", "water"))
        constants.add(Constants("Mobile home park, Mobile home", "470-570", "L/(unit*d)","Wastewater flow rate (USA)", "water"))
        constants.add(Constants("Motel with kitchen, Guest", "210-340", "L/(unit*d)","Wastewater flow rate (USA)", "water"))
        constants.add(Constants("Motel without kitchen, Guest", "190-290", "L/(unit*d)","Wastewater flow rate (USA)", "water"))
        constants.add(Constants("Office, Employee", "25-60", "L/(unit*d)","Wastewater flow rate (USA)", "water"))
        constants.add(Constants("Public restroom, User", "10-20", "L/(unit*d)","Wastewater flow rate (USA)", "water"))
        constants.add(Constants("Restaurant without bar, Customer", "25-40", "L/(unit*d)","Wastewater flow rate (USA)", "water"))
        constants.add(Constants("Restaurant with bar, Customer", "35-45", "L/(unit*d)","Wastewater flow rate (USA)", "water"))
        constants.add(Constants("Shopping center, Employee", "25-55", "L/(unit*d)","Wastewater flow rate (USA)", "water"))
        constants.add(Constants("Shopping center, Parking space", "5-10", "L/(unit*d)","Wastewater flow rate (USA)", "water"))
        constants.add(Constants("Theater, Seat", "10-15", "L/(unit*d)","Wastewater flow rate (USA)", "water"))
        constants.add(Constants("Ks", "25-100", "mg/(L*BOD5)","Growth constant for domestic wastewater (USA)", "water"))
        constants.add(Constants("kd", "0-0.30", "d^-1","Growth constant for domestic wastewater (USA)", "water"))
        constants.add(Constants("um", "1-8", "d^-1","Growth constant for domestic wastewater (USA)", "water"))
        constants.add(Constants("Y", "0.4-0.8", "mg VSS/mg BOD5","Growth constant for domestic wastewater (USA)", "water"))
        constants.add(Constants("Trash racks", "40-150", "mm","Nomenclature of racks and screens", "water"))
        constants.add(Constants("Bar racks, Coarse screens, Fine screens", "6-75", "mm","Nomenclature of racks and screens", "water"))
        constants.add(Constants("Very fine screens", "0.25-1.5", "mm","Nomenclature of racks and screens", "water"))
        constants.add(Constants("Micro-screens", "0.001-0.3", "mm","Nomenclature of racks and screens", "water"))




    }
}