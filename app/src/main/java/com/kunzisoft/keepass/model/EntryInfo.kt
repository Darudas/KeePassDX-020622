/*
 * Copyright 2019 Jeremy Jamet / Kunzisoft.
 *
 * This file is part of KeePassDX.
 *
 *  KeePassDX is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  KeePassDX is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with KeePassDX.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.kunzisoft.keepass.model

import android.os.Parcel
import android.os.Parcelable
import com.kunzisoft.keepass.database.element.Attachment
import com.kunzisoft.keepass.database.element.Database
import com.kunzisoft.keepass.database.element.DateInstant
import com.kunzisoft.keepass.database.element.icon.IconImage
import com.kunzisoft.keepass.database.element.icon.IconImageStandard
import com.kunzisoft.keepass.database.element.security.ProtectedString
import com.kunzisoft.keepass.otp.OtpElement
import com.kunzisoft.keepass.otp.OtpEntryFields.OTP_TOKEN_FIELD
import kotlin.collections.ArrayList

class EntryInfo : Parcelable {

    var id: String = ""
    var title: String = ""
    var icon: IconImage = IconImageStandard()
    var username: String = ""
    var password: String = ""
    var expires: Boolean = false
    var expiryTime: DateInstant = DateInstant.IN_ONE_MONTH
    var url: String = ""
    var notes: String = ""
    var customFields: List<Field> = ArrayList()
    var attachments: List<Attachment> = ArrayList()
    var otpModel: OtpModel? = null

    constructor()

    private constructor(parcel: Parcel) {
        id = parcel.readString() ?: id
        title = parcel.readString() ?: title
        icon = parcel.readParcelable(IconImage::class.java.classLoader) ?: icon
        username = parcel.readString() ?: username
        password = parcel.readString() ?: password
        expires = parcel.readInt() != 0
        expiryTime = parcel.readParcelable(DateInstant::class.java.classLoader) ?: expiryTime
        url = parcel.readString() ?: url
        notes = parcel.readString() ?: notes
        parcel.readList(customFields, Field::class.java.classLoader)
        parcel.readList(attachments, Attachment::class.java.classLoader)
        otpModel = parcel.readParcelable(OtpModel::class.java.classLoader) ?: otpModel
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(title)
        parcel.writeParcelable(icon, flags)
        parcel.writeString(username)
        parcel.writeString(password)
        parcel.writeInt(if (expires) 1 else 0)
        parcel.writeParcelable(expiryTime, flags)
        parcel.writeString(url)
        parcel.writeString(notes)
        parcel.writeArray(customFields.toTypedArray())
        parcel.writeArray(attachments.toTypedArray())
        parcel.writeParcelable(otpModel, flags)
    }

    fun containsCustomFieldsProtected(): Boolean {
        return customFields.any { it.protectedValue.isProtected }
    }

    fun containsCustomFieldsNotProtected(): Boolean {
        return customFields.any { !it.protectedValue.isProtected }
    }

    fun isAutoGeneratedField(field: Field): Boolean {
        return field.name == OTP_TOKEN_FIELD
    }

    fun getGeneratedFieldValue(label: String): String {
        otpModel?.let {
            if (label == OTP_TOKEN_FIELD) {
                return OtpElement(it).token
            }
        }
        return customFields.lastOrNull { it.name == label }?.protectedValue?.toString() ?: ""
    }

    private fun addUniqueField(field: Field, number: Int = 0) {
        var exists = false
        var sameData = false
        val suffix = if (number > 0) number.toString() else ""
        customFields.forEach { currentField ->
            if (currentField.name == field.name + suffix) {
                exists = true
                // Not write the same value again
                if (currentField.protectedValue.stringValue == field.protectedValue.stringValue) {
                    sameData = true
                } else {
                    addUniqueField(currentField, number + 1)
                }
                return
            }
        }
        if (!exists && !sameData)
            (customFields as ArrayList<Field>).add(Field(field.name + suffix, field.protectedValue))
    }

    fun saveSearchInfo(database: Database?, searchInfo: SearchInfo) {
        searchInfo.webDomain?.let { webDomain ->
            // If unable to save web domain in custom field or URL not populated, save in URL
            if (database?.allowEntryCustomFields() != true || url.isEmpty()) {
                val retrievedScheme = searchInfo.webScheme
                val scheme = if (retrievedScheme.isNullOrEmpty()) "http" else retrievedScheme
                url = "$scheme://$webDomain"
            } else {
                // Save web domain in custom field
                addUniqueField(Field(WEB_DOMAIN_FIELD_NAME,
                        ProtectedString(false, webDomain))
                )
            }
        } ?: run {
            // Save application id in custom field
            if (database?.allowEntryCustomFields() == true) {
                searchInfo.applicationId?.let { applicationId ->
                    addUniqueField(Field(APPLICATION_ID_FIELD_NAME,
                            ProtectedString(false, applicationId))
                    )
                }
            }
        }
    }

    companion object {

        const val WEB_DOMAIN_FIELD_NAME = "WebDomain"
        const val APPLICATION_ID_FIELD_NAME = "ApplicationId"

        @JvmField
        val CREATOR: Parcelable.Creator<EntryInfo> = object : Parcelable.Creator<EntryInfo> {
            override fun createFromParcel(parcel: Parcel): EntryInfo {
                return EntryInfo(parcel)
            }

            override fun newArray(size: Int): Array<EntryInfo?> {
                return arrayOfNulls(size)
            }
        }
    }
}
