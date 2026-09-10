package rmjarvis.ultiobserver

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

/** Select ordinary phone tests by default or paired-Wear partners when explicitly requested. */
class UltiObserverTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle) {
        val annotationName = RequiresPairedWear::class.java.name
        if (arguments.getString(PAIRED_WEAR_ONLY_ARGUMENT) == "true") {
            arguments.putString("annotation", annotationName)
        } else {
            arguments.putString("notAnnotation", annotationName)
        }
        super.onCreate(arguments)
    }
}

private const val PAIRED_WEAR_ONLY_ARGUMENT = "pairedWearOnly"
