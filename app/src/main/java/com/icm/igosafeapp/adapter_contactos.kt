package com.icm.igosafeapp

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Filter
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class adapter_contactos(context: Context, private val originalContacts: List<String>) : ArrayAdapter<String>(context, 0, originalContacts) {
    private var filteredContacts: List<String> = originalContacts.toList()
    private val favorites: MutableSet<String> = mutableSetOf()

    init {
        // Cargar favoritos desde SharedPreferences
        loadFavorites()
    }

    override fun getCount(): Int = filteredContacts.size

    override fun getItem(position: Int): String? = filteredContacts[position]

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.activity_adapter_contactos, parent, false)

        val contactName = filteredContacts[position]

        val nameTextView: TextView = view.findViewById(R.id.contact_name)
        val favoriteCheckBox: CheckBox = view.findViewById(R.id.favoritos)

        nameTextView.text = contactName

        // Cargar el estado de favorito desde SharedPreferences
        favoriteCheckBox.isChecked = favorites.contains(contactName)

        favoriteCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                favorites.add(contactName)
            } else {
                favorites.remove(contactName)
            }
            // Guardar el estado actualizado de los favoritos en SharedPreferences
            saveFavorites()
        }

        return view
    }

    private fun saveFavorites() {
        // Guardar los favoritos en SharedPreferences
        val sharedPreferences = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putStringSet("favorites", favorites)
        editor.apply()
    }

    private fun loadFavorites() {
        // Cargar los favoritos desde SharedPreferences
        val sharedPreferences = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
        favorites.addAll(sharedPreferences.getStringSet("favorites", mutableSetOf()) ?: mutableSetOf())
    }

    fun getFavorites(): List<String> {
        return favorites.toList()
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val filterResults = FilterResults()
                if (constraint.isNullOrEmpty()) {
                    filterResults.values = originalContacts
                    filterResults.count = originalContacts.size
                } else {
                    val query = constraint.toString().lowercase()
                    val filtered = originalContacts.filter {
                        it.lowercase().contains(query)
                    }
                    filterResults.values = filtered
                    filterResults.count = filtered.size
                }
                return filterResults
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                @Suppress("UNCHECKED_CAST")
                filteredContacts = results?.values as? List<String> ?: originalContacts
                notifyDataSetChanged()
            }
        }
    }
}

