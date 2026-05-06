package com.sillycrypt.mapper

interface Mapper<T1,T2> {
    fun map(data: T1): T2
}