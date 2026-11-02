package com.example.data.local

import kotlinx.coroutines.flow.Flow

class ProductRepository(private val productDao: ProductDao) {

    val allProducts: Flow<List<ProductEntity>> = productDao.getAllProducts()

    suspend fun getAllProductsSync(): List<ProductEntity> {
        return productDao.getAllProductsSync()
    }

    suspend fun insertProduct(product: ProductEntity): Long {
        return productDao.insertProduct(product)
    }

    suspend fun deleteProductById(id: Long) {
        productDao.deleteProductById(id)
    }

    suspend fun clearAll() {
        productDao.clearAllProducts()
    }
}
