package id.a3.feature.cafe.repository

import id.a3.core.data.local.dao.MasterDataDao
import id.a3.core.data.local.entity.MenuItemEntity
import kotlinx.coroutines.flow.Flow

interface CafeMenuRepository {
    fun observeMenuItems(): Flow<List<MenuItemEntity>>
    suspend fun getMenuItemById(id: String): MenuItemEntity?
}

class DefaultCafeMenuRepository(
    private val masterDataDao: MasterDataDao
) : CafeMenuRepository {
    override fun observeMenuItems(): Flow<List<MenuItemEntity>> {
        return masterDataDao.observeActiveMenuItems()
    }

    override suspend fun getMenuItemById(id: String): MenuItemEntity? {
        return masterDataDao.getMenuItem(id)
    }
}
