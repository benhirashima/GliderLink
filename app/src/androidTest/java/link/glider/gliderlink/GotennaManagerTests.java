package link.glider.gliderlink;

import android.app.Application;
import android.test.ApplicationTestCase;

import com.gotenna.sdk.bluetooth.GTConnectionManager;
import com.gotenna.sdk.commands.GTCommandCenter;

import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@SuppressWarnings("UnusedAssignment")
@RunWith(MockitoJUnitRunner.class)
public class GotennaManagerTests extends ApplicationTestCase<Application>
{
    public GotennaManagerTests()
    {
        super(Application.class);
    }

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        createApplication();
    }

    public void testStates()
    {
        GotennaManager gtMan;
        try
        {
            gtMan = new GotennaManager(getContext(), "7", 12345, GTConnectionManager.getInstance(), GTCommandCenter.getInstance());
        }
        catch (IllegalArgumentException e)
        {
            assertNull(e);
            return;
        }

        gtMan.search();
        assertEquals(gtMan.getState(), GotennaManager.State.SEARCHING);

        gtMan.stop();
        assertEquals(gtMan.getState(), GotennaManager.State.STOPPED);
    }
}
