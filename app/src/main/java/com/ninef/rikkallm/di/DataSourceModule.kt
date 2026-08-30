package com.ninef.rikkallm.di

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.http.HttpHeaders
import io.pebbletemplates.pebble.PebbleEngine
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import io.requery.android.database.sqlite.SQLiteCustomExtension
import kotlinx.serialization.json.Json
import me.rerere.ai.provider.ProviderManager
import me.rerere.common.http.AcceptLanguageBuilder
import com.ninef.rikkallm.BuildConfig
import com.ninef.rikkallm.data.ai.AIRequestInterceptor
import com.ninef.rikkallm.data.ai.RequestLoggingInterceptor
import com.ninef.rikkallm.data.ai.transformers.AssistantTemplateLoader
import com.ninef.rikkallm.data.ai.GenerationHandler
import com.ninef.rikkallm.data.ai.cron.CronJobExecutor
import com.ninef.rikkallm.data.ai.cron.CronScheduler
import com.ninef.rikkallm.data.ai.subagent.SubAgentManager
import com.ninef.rikkallm.data.ai.subagent.SubAgentRunner
import com.ninef.rikkallm.data.ai.transformers.TemplateTransformer
import com.ninef.rikkallm.data.api.RikkaHubAPI
import com.ninef.rikkallm.data.api.SponsorAPI
import com.ninef.rikkallm.data.datastore.SettingsStore
import com.ninef.rikkallm.data.db.AppDatabase
import com.ninef.rikkallm.data.ai.subagent.AgentRunStore
import com.ninef.rikkallm.data.db.RoomAgentRunStore
import com.ninef.rikkallm.data.db.fts.MessageFtsManager
import com.ninef.rikkallm.data.db.fts.SimpleDictManager
import com.ninef.rikkallm.data.db.migrations.Migration_6_7
import com.ninef.rikkallm.data.db.migrations.Migration_11_12
import com.ninef.rikkallm.data.db.migrations.Migration_13_14
import com.ninef.rikkallm.data.db.migrations.Migration_14_15
import com.ninef.rikkallm.data.db.migrations.Migration_15_16
import com.ninef.rikkallm.data.db.migrations.Migration_24_25
import com.ninef.rikkallm.data.ai.mcp.McpManager
import com.ninef.rikkallm.data.sync.webdav.WebDavSync
import me.rerere.search.SearchService
import com.ninef.rikkallm.data.sync.S3Sync
import com.ninef.rikkallm.data.webmount.WebMountManager
import com.ninef.rikkallm.data.webmount.WebMountStore
import com.ninef.rikkallm.data.cliseat.CliSeatManager
import com.ninef.rikkallm.data.cliseat.CliSeatRunner
import com.ninef.rikkallm.data.cliseat.CliSeatStore
import com.ninef.rikkallm.data.deepread.DeepReadRunner
import com.ninef.rikkallm.data.deepread.DeepReadStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.ninef.rikkallm.data.huggingface.HuggingFaceApi
import com.ninef.rikkallm.data.huggingface.ModelScopeApi
import com.ninef.rikkallm.data.huggingface.ModelSourceManager
import com.ninef.rikkallm.data.mnn.LocalEnvironmentDetector
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.Locale
import java.util.concurrent.TimeUnit

val dataSourceModule = module {
    single {
        SettingsStore(context = get(), scope = get())
    }

    single {
        val context: Context = get()
        Room.databaseBuilder(context, AppDatabase::class.java, "rikka_hub")
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(Migration_6_7, Migration_11_12, Migration_13_14, Migration_14_15, Migration_15_16, Migration_24_25)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    val dictDir = SimpleDictManager.extractDict(context)
                    val cursor = db.query("SELECT jieba_dict(?)", arrayOf(dictDir.absolutePath))
                    cursor.use {
                        if (it.moveToFirst()) {
                            val result = it.getString(0)
                            val success = result?.trimEnd('/') == dictDir.absolutePath.trimEnd('/')
                            if (!success) {
                                android.util.Log.e(
                                    "DataSourceModule",
                                    "jieba_dict failed: $result, path=${dictDir.absolutePath}"
                                )
                            }
                        }
                    }
                    db.execSQL(
                        """
                        CREATE VIRTUAL TABLE IF NOT EXISTS message_fts USING fts5(
                            text,
                            node_id UNINDEXED,
                            message_id UNINDEXED,
                            conversation_id UNINDEXED,
                            title UNINDEXED,
                            update_at UNINDEXED,
                            tokenize = 'simple'
                        )
                        """.trimIndent()
                    )
                }
            })
            .openHelperFactory(
                RequerySQLiteOpenHelperFactory(
                    listOf(
                RequerySQLiteOpenHelperFactory.ConfigurationOptions { options ->
                    options.customExtensions.add(
                        SQLiteCustomExtension(
                            context.applicationInfo.nativeLibraryDir + "/libsimple",
                            null
                        )
                    )
                    options
                }
            )))
            .build()
    }

    single {
        AssistantTemplateLoader(settingsStore = get())
    }

    single {
        PebbleEngine.Builder()
            .loader(get<AssistantTemplateLoader>())
            .defaultLocale(Locale.getDefault())
            .autoEscaping(false)
            .build()
    }

    single { TemplateTransformer(engine = get(), settingsStore = get()) }

    single {
        get<AppDatabase>().conversationDao()
    }

    single { get<AppDatabase>().cronJobDao() }

    single {
        CronScheduler(dao = get())
    }

    single {
        CronJobExecutor(
            settingsStore = get(),
            subAgentExecutor = get(),
        )
    }

    single {
        get<AppDatabase>().memoryDao()
    }

    single {
        get<AppDatabase>().memoryItemDao()
    }

    single {
        get<AppDatabase>().genMediaDao()
    }

    single {
        get<AppDatabase>().messageNodeDao()
    }

    single {
        get<AppDatabase>().graphDao()
    }

    single {
        get<AppDatabase>().managedFileDao()
    }

    single {
        get<AppDatabase>().favoriteDao()
    }

    single {
        get<AppDatabase>().workspaceDao()
    }

    single {
        get<AppDatabase>().folderDao()
    }

    single {
        get<AppDatabase>().agentRunDao()
    }

    single<AgentRunStore> {
        RoomAgentRunStore(get())
    }

    single {
        MessageFtsManager(get())
    }

    single { McpManager(settingsStore = get(), appScope = get(), filesManager = get(), appEventBus = get()) }

    single {
        WebMountStore(get())
    }

    single {
        WebMountManager(context = get(), okHttpClient = get())
    }

    single {
        CliSeatStore(get())
    }

    single {
        CliSeatRunner(workspaceManager = get())
    }

    single {
        CliSeatManager(store = get(), runner = get())
    }

    single {
        DeepReadStore(get())
    }

    single {
        DeepReadRunner(
            providerManager = get(),
            settingsStore = get(),
            okHttpClient = get(),
        )
    }

    single<com.ninef.rikkallm.data.ai.subagent.SubAgentExecutor> {
        SubAgentRunner(providerManager = get())
    }

    single {
        SubAgentManager(
            executor = get(),
            appScope = get(),
            store = get(),
            eventBus = get()
        )
    }

    single {
        GenerationHandler(
            context = get(),
            providerManager = get(),
            json = get(),
            memoryRepo = get(),
            subAgentManager = get()
        )
    }

    single<OkHttpClient> {
        val acceptLang = AcceptLanguageBuilder.fromAndroid(get())
            .build()
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(120, TimeUnit.SECONDS)
            .followSslRedirects(true)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestBuilder = originalRequest.newBuilder()
                    .addHeader(HttpHeaders.AcceptLanguage, acceptLang)

                if (originalRequest.header(HttpHeaders.UserAgent) == null) {
                    requestBuilder.addHeader(HttpHeaders.UserAgent, "RikkaHub-Android/${BuildConfig.VERSION_NAME}")
                }

                chain.proceed(requestBuilder.build())
            }
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val contentTypeHeader = request.header("Content-Type")
                if (
                    contentTypeHeader != null &&
                    contentTypeHeader.contains(";") &&
                    contentTypeHeader.substringBefore(";").trim().equals("application/json", ignoreCase = true)
                ) {
                    chain.proceed(
                        request.newBuilder()
                            .header("Content-Type", contentTypeHeader.substringBefore(";").trim())
                            .build()
                    )
                } else {
                    chain.proceed(request)
                }
            }
            .addNetworkInterceptor(RequestLoggingInterceptor())
            .addInterceptor(AIRequestInterceptor())
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            })
            .build().also { SearchService.init(it, get()) }
    }

    single {
        SponsorAPI.create(get())
    }

    single {
        ProviderManager(client = get(), context = get())
    }

    single {
        WebDavSync(
            settingsStore = get(),
            json = get(),
            context = get(),
            httpClient = get()
        )
    }

    single<HttpClient> {
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(20, TimeUnit.SECONDS)
                    readTimeout(10, TimeUnit.MINUTES)
                    writeTimeout(120, TimeUnit.SECONDS)
                    followSslRedirects(true)
                    followRedirects(true)
                    retryOnConnectionFailure(true)
                }
            }
        }
    }

    single {
        S3Sync(
            settingsStore = get(),
            json = get(),
            context = get(),
            httpClient = get()
        )
    }

    single<Retrofit> {
        Retrofit.Builder()
            .baseUrl("https://api.rikka-ai.com")
            .addConverterFactory(get<Json>().asConverterFactory("application/json; charset=UTF8".toMediaType()))
            .build()
    }

    single<RikkaHubAPI> {
        get<Retrofit>().create(RikkaHubAPI::class.java)
    }

    single { HuggingFaceApi(get()) }

    single { ModelScopeApi(get()) }

    single { ModelSourceManager(get()) }

    single { LocalEnvironmentDetector(get(), get()) }
}
