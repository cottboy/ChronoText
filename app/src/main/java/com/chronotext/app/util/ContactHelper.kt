package com.chronotext.app.util

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

/**
 * 通讯录读取：配合系统联系人选择器（ACTION_PICK）使用。
 * 返回的 URI 由选择器授予临时读取权，读号码需查询电话表，
 * 因此应用还需持有 READ_CONTACTS 权限。
 */
object ContactHelper {

    /**
     * 从选择器返回的联系人 URI 读取（姓名, 号码）。
     * 多号码联系人取第一个；读不到返回 null。
     */
    fun readContact(context: Context, uri: Uri): Pair<String, String>? {
        var name = ""
        var contactId = -1L
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            if (c.moveToFirst()) {
                contactId = c.getLong(0)
                name = c.getString(1) ?: ""
            }
        } ?: return null
        if (contactId < 0) return null

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )?.use { c ->
            if (c.moveToFirst()) {
                val number = c.getString(0)?.takeIf { it.isNotBlank() } ?: return null
                return name to number
            }
        }
        return null
    }
}
