package com.icm.igosafeapp

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/*class adapter_contactos : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_adapter_contactos)

    }
}*/
class adapter_contactos(context: Context, private val contacts: List<String>) : ArrayAdapter<String>(context, 0, contacts) {
    private val favorites: MutableList<String> = mutableListOf()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.activity_adapter_contactos, parent, false)

        val contactName = contacts[position]

        val nameTextView: TextView = view.findViewById(R.id.contact_name)
       // val profileImageView: ImageView = view.findViewById(R.id.profile_icon)
        val favoriteCheckBox: CheckBox = view.findViewById(R.id.favoritos)

        nameTextView.text = contactName
        // Aquí puedes asignar un icono diferente basado en el contacto si es necesario
        // profileImageView.setImageResource(R.drawable.ic_profile)

        favoriteCheckBox.isChecked = favorites.contains(contactName)

        favoriteCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                favorites.add(contactName)
            } else {
                favorites.remove(contactName)
            }
        }
        return view
    }
    fun getFavorites(): List<String> {
        return favorites
    }
}