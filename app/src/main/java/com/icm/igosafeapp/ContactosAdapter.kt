package com.icm.igosafeapp

import Contactos
import android.annotation.SuppressLint
import android.content.Context
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.ImageView
import android.widget.TextView

class ContactosAdapter(context: Context, private val originalContacts: List<Contactos>) : ArrayAdapter<Contactos>(context, 0, originalContacts) {
    private var filteredContacts: List<Contactos> = originalContacts

    override fun getCount(): Int = filteredContacts.size

    override fun getItem(position: Int): Contactos? = filteredContacts[position]

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
                        it.nickname.lowercase().contains(query) || it.fullName.lowercase().contains(query)
                    }
                    filterResults.values = filtered
                    filterResults.count = filtered.size
                }
                return filterResults
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredContacts = results?.values as? List<Contactos> ?: originalContacts
                notifyDataSetChanged()
            }
        }
    }

    @SuppressLint("Range")
    fun getPhoneNumberFromContact(contactName: String): String? {
        val phoneNumber: String? = null
        val contentResolver = context.contentResolver
        val uri = ContactsContract.Contacts.CONTENT_URI
        val projection = arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME)

        val cursor = contentResolver.query(uri, projection, ContactsContract.Contacts.DISPLAY_NAME + " = ?", arrayOf(contactName), null)

        cursor?.let {
            if (it.moveToFirst()) {
                val contactId = it.getString(it.getColumnIndex(ContactsContract.Contacts._ID))
                val phoneCursor = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                    arrayOf(contactId),
                    null
                )
                if (phoneCursor != null && phoneCursor.moveToFirst()) {
                    val number = phoneCursor.getString(phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    phoneCursor.close()
                    return number
                }
                phoneCursor?.close()
            }
            it.close()
        }

        return phoneNumber
    }
}
