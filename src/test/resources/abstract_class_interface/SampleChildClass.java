/*
 * Class with constructor that passes parameter to super.
 */

class SampleChildClass extends Exception {
    
    // Constructor that passes message to super
    public SampleChildClass(String message) {
        super(message);
    }

    // Constructor that calls this()
    public SampleChildClass() {
        this("Default error");
    }

}
