package com.whitertc.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module

@Module(
    includes = [
        DatabaseModule::class
    ]
)
@ComponentScan("com.whitertc")
@Configuration
class AppModule
