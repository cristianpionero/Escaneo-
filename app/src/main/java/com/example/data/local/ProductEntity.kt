package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Representa un producto guardado en la tienda con su huella dactilar visual
 * calculada a partir de las 20 fotos tomadas por la cámara.
 */
@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val price: Long, // Precio en pesos colombianos (COP), ej: 2500
    val sampleCount: Int = 20,
    val featureVector: String, // Vector serializado de 128 dimensiones (huella visual)
    val createdAt: Long = System.currentTimeMillis()
)
