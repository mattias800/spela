package com.spela.player.presentation.viewmodel

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.spela.player.data.device.DeviceManager
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.domain.model.AuthTokens
import com.spela.player.domain.model.User
import com.spela.player.domain.repository.AuthRepository
import com.spela.player.domain.usecase.LoginUseCase
import com.spela.player.domain.usecase.RegisterUseCase
import com.spela.player.presentation.intent.LoginIntent
import com.spela.player.util.DispatcherProvider
import com.spela.player.test.NoOpMockEngineFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private val fakeAuthRepository = FakeAuthRepository()
    private lateinit var deviceManager: DeviceManager

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SpelaDatabase.Schema.create(driver)
        val database = SpelaDatabase(driver)
        val apiClient = SpelaApiClient(NoOpMockEngineFactory, TokenManager())
        deviceManager = DeviceManager(database, apiClient)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): LoginViewModel {
        val scope = CoroutineScope(testDispatcher)
        return LoginViewModel(
            loginUseCase = LoginUseCase(fakeAuthRepository, deviceManager),
            registerUseCase = RegisterUseCase(fakeAuthRepository, deviceManager),
            dispatchers = testDispatchers,
            scope = scope,
        )
    }

    @Test
    fun initialStateIsEmpty() {
        val vm = createViewModel()
        val state = vm.state.value
        assertEquals("", state.serverUrl)
        assertEquals("", state.username)
        assertEquals("", state.email)
        assertEquals("", state.password)
        assertFalse(state.isLoading)
        assertFalse(state.isLoggedIn)
        assertNull(state.error)
    }

    @Test
    fun setFieldsUpdatesState() {
        val vm = createViewModel()
        vm.onIntent(LoginIntent.SetServerUrl("http://localhost:8080"))
        vm.onIntent(LoginIntent.SetUsername("testuser"))
        vm.onIntent(LoginIntent.SetEmail("test@example.com"))
        vm.onIntent(LoginIntent.SetPassword("pass123"))

        val state = vm.state.value
        assertEquals("http://localhost:8080", state.serverUrl)
        assertEquals("testuser", state.username)
        assertEquals("test@example.com", state.email)
        assertEquals("pass123", state.password)
    }

    @Test
    fun submitWithEmptyFieldsShowsError() {
        val vm = createViewModel()
        vm.onIntent(LoginIntent.Submit)

        val state = vm.state.value
        assertEquals("All fields are required", state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun registerWithoutEmailShowsError() {
        val vm = createViewModel()
        vm.onIntent(LoginIntent.SetServerUrl("http://localhost:8080"))
        vm.onIntent(LoginIntent.SetUsername("testuser"))
        vm.onIntent(LoginIntent.SetPassword("pass123"))
        vm.onIntent(LoginIntent.ToggleRegisterMode)
        vm.onIntent(LoginIntent.Submit)

        assertEquals("Email is required for registration", vm.state.value.error)
    }

    @Test
    fun successfulLoginSetsLoggedIn() = runTest(testDispatcher) {
        fakeAuthRepository.shouldSucceed = true
        val vm = createViewModel()

        vm.onIntent(LoginIntent.SetServerUrl("http://localhost:8080"))
        vm.onIntent(LoginIntent.SetUsername("testuser"))
        vm.onIntent(LoginIntent.SetPassword("pass123"))
        vm.onIntent(LoginIntent.Submit)

        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.isLoggedIn)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun failedLoginShowsError() = runTest(testDispatcher) {
        fakeAuthRepository.shouldSucceed = false
        val vm = createViewModel()

        vm.onIntent(LoginIntent.SetServerUrl("http://localhost:8080"))
        vm.onIntent(LoginIntent.SetUsername("testuser"))
        vm.onIntent(LoginIntent.SetPassword("wrongpass"))
        vm.onIntent(LoginIntent.Submit)

        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoggedIn)
        assertFalse(state.isLoading)
        assertNotNull(state.error)
    }

    @Test
    fun successfulRegisterSetsLoggedIn() = runTest(testDispatcher) {
        fakeAuthRepository.shouldSucceed = true
        val vm = createViewModel()

        vm.onIntent(LoginIntent.ToggleRegisterMode)
        vm.onIntent(LoginIntent.SetServerUrl("http://localhost:8080"))
        vm.onIntent(LoginIntent.SetUsername("newuser"))
        vm.onIntent(LoginIntent.SetEmail("new@example.com"))
        vm.onIntent(LoginIntent.SetPassword("pass123"))
        vm.onIntent(LoginIntent.Submit)

        advanceUntilIdle()

        assertTrue(vm.state.value.isLoggedIn)
    }

    @Test
    fun toggleRegisterMode() {
        val vm = createViewModel()
        assertFalse(vm.state.value.isRegisterMode)

        vm.onIntent(LoginIntent.ToggleRegisterMode)
        assertTrue(vm.state.value.isRegisterMode)

        vm.onIntent(LoginIntent.ToggleRegisterMode)
        assertFalse(vm.state.value.isRegisterMode)
    }

    @Test
    fun dismissErrorClearsError() {
        val vm = createViewModel()
        vm.onIntent(LoginIntent.Submit) // triggers "All fields required" error
        assertNotNull(vm.state.value.error)

        vm.onIntent(LoginIntent.DismissError)
        assertNull(vm.state.value.error)
    }
}

/** Fake AuthRepository for testing */
class FakeAuthRepository : AuthRepository {
    var shouldSucceed = true

    private val successTokens = AuthTokens(
        accessToken = "test-access-token",
        refreshToken = "test-refresh-token",
    )

    private var stored: AuthTokens? = null
    private var loggedIn = false

    override suspend fun login(serverUrl: String, username: String, password: String): Result<AuthTokens> {
        return if (shouldSucceed) {
            loggedIn = true
            Result.success(successTokens)
        } else {
            Result.failure(Exception("Invalid credentials"))
        }
    }

    override suspend fun register(serverUrl: String, username: String, email: String, password: String): Result<AuthTokens> {
        return if (shouldSucceed) {
            loggedIn = true
            Result.success(successTokens)
        } else {
            Result.failure(Exception("Registration failed"))
        }
    }

    override suspend fun refreshToken(serverUrl: String, refreshToken: String): Result<AuthTokens> {
        return Result.success(successTokens)
    }

    override suspend fun getCurrentUser(): Result<User> {
        return Result.success(User("1", "testuser", "test@example.com", "user"))
    }

    override suspend fun getStoredTokens(): AuthTokens? = stored
    override suspend fun storeTokens(tokens: AuthTokens) { stored = tokens }
    override suspend fun clearTokens() { stored = null; loggedIn = false }
    override fun isLoggedIn(): Boolean = loggedIn
}
