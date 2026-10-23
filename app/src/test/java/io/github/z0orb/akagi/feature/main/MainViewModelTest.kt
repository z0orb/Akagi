package io.github.z0orb.akagi.feature.main

import io.github.z0orb.akagi.core.common.DispatcherProvider
import io.github.z0orb.akagi.core.crash.CrashLogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val io: TestDispatcher = dispatcher
    }

    private class FakeCrashLogStore(var report: String?) : CrashLogStore {
        override fun write(report: String) {
            this.report = report
        }

        override fun read(): String? = report

        override fun clear() {
            report = null
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `no crash last time means no report`() = runTest(dispatcher) {
        val viewModel = MainViewModel(FakeCrashLogStore(null), dispatchers)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.crashReport)
    }

    @Test
    fun `a saved crash is offered once, then cleared`() = runTest(dispatcher) {
        val store = FakeCrashLogStore("Akagi crash report")
        val viewModel = MainViewModel(store, dispatchers)
        advanceUntilIdle()
        assertEquals("Akagi crash report", viewModel.uiState.value.crashReport)

        viewModel.onCrashReportHandled()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.crashReport)
        assertNull(store.report)
    }
}
