package com.microsoft.azure.mobile.crashes;

import android.content.Context;

import com.microsoft.azure.mobile.MobileCenter;
import com.microsoft.azure.mobile.MobileCenterHandler;
import com.microsoft.azure.mobile.crashes.ingestion.models.Exception;
import com.microsoft.azure.mobile.crashes.ingestion.models.ManagedErrorLog;
import com.microsoft.azure.mobile.crashes.utils.ErrorLogHelper;
import com.microsoft.azure.mobile.ingestion.models.Log;
import com.microsoft.azure.mobile.ingestion.models.json.LogSerializer;
import com.microsoft.azure.mobile.utils.HandlerUtils;
import com.microsoft.azure.mobile.utils.MobileCenterLog;
import com.microsoft.azure.mobile.utils.async.MobileCenterFuture;
import com.microsoft.azure.mobile.utils.storage.StorageHelper;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentMatcher;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.rule.PowerMockRule;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static com.microsoft.azure.mobile.utils.PrefStorageConstants.KEY_ENABLED;
import static junit.framework.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.doAnswer;
import static org.powermock.api.mockito.PowerMockito.doThrow;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyNoMoreInteractions;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

@PrepareForTest({MobileCenter.class, WrapperSdkExceptionManager.class, MobileCenterLog.class, StorageHelper.PreferencesStorage.class, StorageHelper.InternalStorage.class, Crashes.class, ErrorLogHelper.class, HandlerUtils.class})
public class WrapperSdkExceptionManagerTest {

    private static final String CRASHES_ENABLED_KEY = KEY_ENABLED + "_" + Crashes.getInstance().getServiceName();

    @Rule
    public final PowerMockRule rule = new PowerMockRule();

    @Rule
    public final TemporaryFolder errorStorageDirectory = new TemporaryFolder();

    @Before
    public void setUp() {
        Crashes.unsetInstance();
        mockStatic(MobileCenter.class);
        mockStatic(StorageHelper.PreferencesStorage.class);
        mockStatic(StorageHelper.InternalStorage.class);
        mockStatic(MobileCenterLog.class);
        mockStatic(ErrorLogHelper.class);
        when(ErrorLogHelper.getErrorStorageDirectory()).thenReturn(errorStorageDirectory.getRoot());
        ManagedErrorLog errorLogMock = mock(ManagedErrorLog.class);
        when(errorLogMock.getId()).thenReturn(UUID.randomUUID());
        when(ErrorLogHelper.createErrorLog(any(Context.class), any(Thread.class), any(Exception.class), Matchers.<Map<Thread, StackTraceElement[]>>any(), anyLong(), anyBoolean()))
                .thenReturn(errorLogMock);

        @SuppressWarnings("unchecked")
        MobileCenterFuture<Boolean> future = (MobileCenterFuture<Boolean>) mock(MobileCenterFuture.class);
        when(MobileCenter.isEnabled()).thenReturn(future);
        when(future.get()).thenReturn(true);
        when(StorageHelper.PreferencesStorage.getBoolean(CRASHES_ENABLED_KEY, true)).thenReturn(true);

        /* Mock handlers. */
        mockStatic(HandlerUtils.class);
        Answer<Void> runNow = new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                ((Runnable) invocation.getArguments()[0]).run();
                return null;
            }
        };
        doAnswer(runNow).when(HandlerUtils.class);
        HandlerUtils.runOnUiThread(any(Runnable.class));
        MobileCenterHandler handler = mock(MobileCenterHandler.class);
        Crashes.getInstance().onStarting(handler);
        doAnswer(runNow).when(handler).post(any(Runnable.class), any(Runnable.class));
    }

    @Test
    public void constructWrapperSdkExceptionManager() {
        new WrapperSdkExceptionManager();
    }

    @Test
    public void loadWrapperExceptionData() throws java.lang.Exception {
        doThrow(new IOException()).when(StorageHelper.InternalStorage.class);
        StorageHelper.InternalStorage.readObject(any(File.class));
        assertNull(WrapperSdkExceptionManager.loadWrapperExceptionData(UUID.randomUUID()));
        doThrow(new ClassNotFoundException()).when(StorageHelper.InternalStorage.class);
        StorageHelper.InternalStorage.readObject(any(File.class));
        assertNull(WrapperSdkExceptionManager.loadWrapperExceptionData(UUID.randomUUID()));
        assertNull(WrapperSdkExceptionManager.loadWrapperExceptionData(null));
    }

    @Test
    public void deleteWrapperExceptionDataWithNullId() {

        /* Delete null does nothing. */
        WrapperSdkExceptionManager.deleteWrapperExceptionData(null);
        verifyStatic(never());
        StorageHelper.InternalStorage.delete(any(File.class));
        verifyStatic();
        MobileCenterLog.error(eq(Crashes.LOG_TAG), anyString());
    }

    @Test
    public void deleteWrapperExceptionDataWithMissingId() {

        /* Delete with file not found does nothing. */
        WrapperSdkExceptionManager.deleteWrapperExceptionData(UUID.randomUUID());
        verifyStatic(never());
        StorageHelper.InternalStorage.delete(any(File.class));
        verifyStatic(never());
        MobileCenterLog.error(eq(Crashes.LOG_TAG), anyString());
    }

    @Test
    public void deleteWrapperExceptionDataWithLoadingError() throws java.lang.Exception {

        /* Delete with file that cannot be loaded because invalid content should just log an error. */
        File file = mock(File.class);
        whenNew(File.class).withAnyArguments().thenReturn(file);
        when(file.exists()).thenReturn(true);
        WrapperSdkExceptionManager.deleteWrapperExceptionData(UUID.randomUUID());
        verifyStatic();
        StorageHelper.InternalStorage.delete(any(File.class));
        verifyStatic();
        MobileCenterLog.error(eq(Crashes.LOG_TAG), anyString());
    }

    @Test
    public void saveWrapperSdkCrash() throws JSONException, IOException {
        LogSerializer logSerializer = Mockito.mock(LogSerializer.class);
        when(logSerializer.serializeLog(any(ManagedErrorLog.class))).thenReturn("mock");
        Crashes.getInstance().setLogSerializer(logSerializer);
        byte[] data = new byte[]{'d'};
        WrapperSdkExceptionManager.saveWrapperException(Thread.currentThread(), new Exception(), data);
        verifyStatic();
        StorageHelper.InternalStorage.writeObject(any(File.class), eq(data));

        /* We can't do it twice in the same process. */
        data = new byte[]{'e'};
        WrapperSdkExceptionManager.saveWrapperException(Thread.currentThread(), new Exception(), data);
        verifyStatic(never());
        StorageHelper.InternalStorage.writeObject(any(File.class), eq(data));
    }

    @Test
    public void saveWrapperSdkCrashFailsToCreateThrowablePlaceholder() throws java.lang.Exception {
        LogSerializer logSerializer = Mockito.mock(LogSerializer.class);
        when(logSerializer.serializeLog(any(ManagedErrorLog.class))).thenReturn("mock");
        Crashes.getInstance().setLogSerializer(logSerializer);
        File throwableFile = mock(File.class);
        whenNew(File.class).withParameterTypes(String.class, String.class).withArguments(anyString(), argThat(new ArgumentMatcher<String>() {

            @Override
            public boolean matches(Object argument) {
                return String.valueOf(argument).endsWith(ErrorLogHelper.THROWABLE_FILE_EXTENSION);
            }
        })).thenReturn(throwableFile);
        when(throwableFile.createNewFile()).thenReturn(false);
        byte[] data = new byte[]{'d'};
        WrapperSdkExceptionManager.saveWrapperException(Thread.currentThread(), new Exception(), data);
        verifyStatic();
        MobileCenterLog.error(anyString(), anyString(), argThat(new ArgumentMatcher<Throwable>() {

            @Override
            public boolean matches(Object argument) {
                return argument instanceof IOException;
            }
        }));

        /* Second call is ignored. */
        data = new byte[]{'e'};
        WrapperSdkExceptionManager.saveWrapperException(Thread.currentThread(), new Exception(), data);

        /* No more error. */
        verifyStatic();
        MobileCenterLog.error(anyString(), anyString(), argThat(new ArgumentMatcher<Throwable>() {

            @Override
            public boolean matches(Object argument) {
                return argument instanceof IOException;
            }
        }));
    }

    @Test
    public void saveWrapperSdkCrashFailsWithJSONException() throws JSONException {
        LogSerializer logSerializer = Mockito.mock(LogSerializer.class);
        when(logSerializer.serializeLog(any(ManagedErrorLog.class))).thenThrow(new JSONException("mock"));
        Crashes.getInstance().setLogSerializer(logSerializer);
        WrapperSdkExceptionManager.saveWrapperException(Thread.currentThread(), new Exception(), new byte[]{'d'});
        verifyStatic();
        MobileCenterLog.error(anyString(), anyString(), argThat(new ArgumentMatcher<Throwable>() {

            @Override
            public boolean matches(Object argument) {
                return argument instanceof JSONException;
            }
        }));

        /* Second call is ignored. */
        WrapperSdkExceptionManager.saveWrapperException(Thread.currentThread(), new Exception(), new byte[]{'e'});

        /* No more error. */
        verifyStatic();
        MobileCenterLog.error(anyString(), anyString(), argThat(new ArgumentMatcher<Throwable>() {

            @Override
            public boolean matches(Object argument) {
                return argument instanceof JSONException;
            }
        }));
    }

    @Test
    public void saveWrapperSdkCrashFailsWithIOException() throws IOException, JSONException {
        doThrow(new IOException()).when(StorageHelper.InternalStorage.class);
        StorageHelper.InternalStorage.write(any(File.class), anyString());
        LogSerializer logSerializer = Mockito.mock(LogSerializer.class);
        when(logSerializer.serializeLog(any(ManagedErrorLog.class))).thenReturn("mock");
        Crashes.getInstance().setLogSerializer(logSerializer);
        WrapperSdkExceptionManager.saveWrapperException(Thread.currentThread(), new Exception(), new byte[]{'d'});
        verifyStatic();
        MobileCenterLog.error(anyString(), anyString(), argThat(new ArgumentMatcher<Throwable>() {

            @Override
            public boolean matches(Object argument) {
                return argument instanceof IOException;
            }
        }));

        /* Second call is ignored. */
        WrapperSdkExceptionManager.saveWrapperException(Thread.currentThread(), new Exception(), new byte[]{'e'});

        /* No more error. */
        verifyStatic();
        MobileCenterLog.error(anyString(), anyString(), argThat(new ArgumentMatcher<Throwable>() {

            @Override
            public boolean matches(Object argument) {
                return argument instanceof IOException;
            }
        }));
    }

    @Test
    public void saveWrapperSdkCrashFailsWithIOExceptionAfterLog() throws IOException, JSONException {
        byte[] data = {'d'};
        doThrow(new IOException()).when(StorageHelper.InternalStorage.class);
        StorageHelper.InternalStorage.writeObject(any(File.class), eq(data));
        LogSerializer logSerializer = Mockito.mock(LogSerializer.class);
        when(logSerializer.serializeLog(any(ManagedErrorLog.class))).thenReturn("mock");
        Crashes.getInstance().setLogSerializer(logSerializer);
        WrapperSdkExceptionManager.saveWrapperException(Thread.currentThread(), new Exception(), data);
        verifyStatic();
        MobileCenterLog.error(anyString(), anyString(), argThat(new ArgumentMatcher<Throwable>() {

            @Override
            public boolean matches(Object argument) {
                return argument instanceof IOException;
            }
        }));

        /* Second call is ignored. */
        WrapperSdkExceptionManager.saveWrapperException(Thread.currentThread(), new Exception(), new byte[]{'e'});

        /* No more error. */
        verifyStatic();
        MobileCenterLog.error(anyString(), anyString(), argThat(new ArgumentMatcher<Throwable>() {

            @Override
            public boolean matches(Object argument) {
                return argument instanceof IOException;
            }
        }));
    }

    @Test
    public void saveWrapperExceptionWhenSDKDisabled() throws JSONException {
        when(StorageHelper.PreferencesStorage.getBoolean(CRASHES_ENABLED_KEY, true)).thenReturn(false);
        LogSerializer logSerializer = Mockito.mock(LogSerializer.class);
        Crashes.getInstance().setLogSerializer(logSerializer);
        WrapperSdkExceptionManager.saveWrapperException(Thread.currentThread(), new Exception(), new byte[]{'d'});
        verify(logSerializer, never()).serializeLog(any(Log.class));
        verifyNoMoreInteractions(ErrorLogHelper.class);
    }
}