package com.johnpaulpatigas.motofuel.viewmodel

import com.johnpaulpatigas.motofuel.data.model.RefuelingRecord
import com.johnpaulpatigas.motofuel.repository.FakeRefuelingDao
import com.johnpaulpatigas.motofuel.data.repository.RefuelingRepositoryImpl
import com.johnpaulpatigas.motofuel.ui.viewmodel.FuelViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FuelViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var dao: FakeRefuelingDao
    private lateinit var repository: RefuelingRepositoryImpl
    private lateinit var viewModel: FuelViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dao = FakeRefuelingDao()
        repository = RefuelingRepositoryImpl(dao, dispatcher = testDispatcher)
        viewModel = FuelViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `openAddDialog and closeAddDialog update UI state`() {
        assertFalse(viewModel.uiState.value.isAddDialogOpen)
        viewModel.openAddDialog()
        assertTrue(viewModel.uiState.value.isAddDialogOpen)
        viewModel.closeAddDialog()
        assertFalse(viewModel.uiState.value.isAddDialogOpen)
    }

    @Test
    fun `openEditDialog and closeEditDialog update UI state`() {
        val dummyRecord = RefuelingRecord(id = 1, odometerKm = 1000.0, litersAdded = 10.0)
        assertNull(viewModel.uiState.value.recordToEdit)

        viewModel.openEditDialog(dummyRecord)
        assertEquals(dummyRecord, viewModel.uiState.value.recordToEdit)

        viewModel.closeEditDialog()
        assertNull(viewModel.uiState.value.recordToEdit)
    }

    @Test
    fun `openDeleteDialog and closeDeleteDialog update UI state`() {
        val dummyRecord = RefuelingRecord(id = 1, odometerKm = 1000.0, litersAdded = 10.0)
        assertNull(viewModel.uiState.value.recordToDelete)

        viewModel.openDeleteDialog(dummyRecord)
        assertEquals(dummyRecord, viewModel.uiState.value.recordToDelete)

        viewModel.closeDeleteDialog()
        assertNull(viewModel.uiState.value.recordToDelete)
    }

    @Test
    fun `addRecord with invalid format sets validation error`() {
        viewModel.openAddDialog()
        viewModel.addRecord("abc", "10.0")

        assertNotNull(viewModel.uiState.value.validationError)
        assertEquals("Please enter valid numbers for both fields.", viewModel.uiState.value.validationError)
    }

    @Test
    fun `addRecord with valid input saves record and closes dialog`() = runBlocking {
        viewModel.openAddDialog()
        viewModel.addRecord("1000.0", "10.0")

        assertFalse(viewModel.uiState.value.isAddDialogOpen)
        assertNull(viewModel.uiState.value.validationError)
        assertEquals("Refueling record saved.", viewModel.uiState.value.userMessage)
    }
}
