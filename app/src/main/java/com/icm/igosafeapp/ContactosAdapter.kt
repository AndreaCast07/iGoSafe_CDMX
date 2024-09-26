package com.icm.igosafeapp

import Contactos
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView

class ContactosAdapter(context: Context, contacts: List<Contactos>) : ArrayAdapter<Contactos>(context, 0, contacts) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val contact = getItem(position)

        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.activity_adapter_contact_final, parent, false)

        val icon: ImageView = view.findViewById(R.id.contactIcon)
        val apodo: TextView = view.findViewById(R.id.contactNickname)
        val nombreCompleto: TextView = view.findViewById(R.id.contactFullName)

        contact?.let {
            icon.setImageResource(it.iconResId)
            apodo.text = it.nickname
            nombreCompleto.text = it.fullName
        }
        return view
    }
}
