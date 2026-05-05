package com.libsillycrypt.workprofile.domain.entities

import android.annotation.TargetApi
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Parcel
import android.os.Parcelable


class ApplicationInfoWrapper : Parcelable {
    var info: ApplicationInfo? = null
        private set
    var label: String? = null
        private set
    var isHidden: Boolean = false
        private set

    private constructor()

    constructor(info: ApplicationInfo?) {
        this.info = info
    }

    fun loadLabel(pm: PackageManager): ApplicationInfoWrapper {
        this.label = pm.getApplicationLabel(this.info!!).toString()
        return this
    }

    fun setHidden(hidden: Boolean): ApplicationInfoWrapper {
        this.isHidden = hidden
        return this
    }

    val packageName: String?
        get() = info!!.packageName

    val sourceDir: String?
        get() = info!!.sourceDir

    val splitApks: Array<String?>?
        get() = info!!.splitSourceDirs

    val enabled: Boolean
        get() = info!!.enabled

    val isSystem: Boolean
        get() = (info!!.flags and ApplicationInfo.FLAG_SYSTEM) != 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(this.info, flags)
        dest.writeString(this.label)
        dest.writeByte((if (this.isHidden) 1 else 0).toByte())
    }

    override fun describeContents(): Int {
        return info!!.packageName.hashCode()
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ApplicationInfoWrapper?> =
            object : Parcelable.Creator<ApplicationInfoWrapper?> {
                override fun newArray(size: Int): Array<ApplicationInfoWrapper?> {
                    return arrayOfNulls<ApplicationInfoWrapper>(size)
                }

                override fun createFromParcel(source: Parcel): ApplicationInfoWrapper {
                    val info = ApplicationInfoWrapper()
                    info.info =
                        source.readParcelable<ApplicationInfo?>(ApplicationInfo::class.java.getClassLoader())
                    info.label = source.readString()
                    info.isHidden = source.readByte().toInt() != 0
                    return info
                }
            }
    }
}