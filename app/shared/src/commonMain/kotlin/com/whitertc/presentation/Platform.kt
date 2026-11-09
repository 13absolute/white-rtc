package com.whitertc

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform