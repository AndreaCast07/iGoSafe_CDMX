package com.icm.igosafeapp

import android.content.Context
import android.graphics.Typeface
import android.text.style.CharacterStyle
import android.text.style.StyleSpan
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import java.util.concurrent.TimeUnit

class PlaceAutocompleteAdapter(
    context: Context,
    private val placesClient: PlacesClient
) : ArrayAdapter<AutocompletePrediction>(context, android.R.layout.simple_expandable_list_item_2, android.R.id.text1), Filterable {

    private var resultList: List<AutocompletePrediction> = arrayListOf()
    private val STYLE_BOLD: CharacterStyle = StyleSpan(Typeface.BOLD)

    // Bounds aproximados para la Ciudad de México
    private val cdmxBounds = RectangularBounds.newInstance(
        LatLng(19.0482, -99.3649), // Suroeste
        LatLng(19.5928, -98.9403)  // Noreste
    )

    override fun getCount(): Int = resultList.size

    override fun getItem(position: Int): AutocompletePrediction = resultList[position]

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = super.getView(position, convertView, parent)
        val prediction = getItem(position)

        val text1 = row.findViewById<TextView>(android.R.id.text1)
        val text2 = row.findViewById<TextView>(android.R.id.text2)

        text1.text = prediction.getPrimaryText(STYLE_BOLD)
        text2.text = prediction.getSecondaryText(STYLE_BOLD)

        return row
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                if (constraint != null) {
                    resultList = getAutocomplete(constraint)
                    results.values = resultList
                    results.count = resultList.size
                }
                return results
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                if (results != null && results.count > 0) {
                    notifyDataSetChanged()
                } else {
                    notifyDataSetInvalidated()
                }
            }

            override fun convertResultToString(resultValue: Any?): CharSequence {
                return if (resultValue is AutocompletePrediction) {
                    resultValue.getFullText(null)
                } else {
                    super.convertResultToString(resultValue)
                }
            }
        }
    }

    private fun getAutocomplete(constraint: CharSequence): List<AutocompletePrediction> {
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(constraint.toString())
            .setLocationRestriction(cdmxBounds) // Restringir a CDMX
            .setCountries("MX") // Asegurar que sea en México
            .build()

        val task = placesClient.findAutocompletePredictions(request)

        return try {
            Tasks.await(task, 60, TimeUnit.SECONDS)
            task.result?.autocompletePredictions ?: arrayListOf()
        } catch (e: Exception) {
            arrayListOf()
        }
    }
}
