package link.glider.gliderlink;

import link.glider.gliderlink.utils.NumberUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;

/**
 * Created by bhirashi on 12/28/16.
 */

@RunWith(MockitoJUnitRunner.class)
public class NumberUtilsTests
{
	@Test
	public void roundToHundreds_test()
	{
		long result = NumberUtils.roundToHundreds(1234.1d);
		assertEquals(12, result);
	}

	@Test
	public void roundToHundreds_testNegative()
	{
		long result = NumberUtils.roundToHundreds(-1234.1d);
		assertEquals(-12, result);
	}

	@Test
	public void doubleRoundToShort_testMax()
	{
		short result = NumberUtils.doubleRoundToShort(Short.MAX_VALUE + 1);
		assertEquals(Short.MAX_VALUE, result);
	}

	@Test
	public void doubleRoundToShort_testMin()
	{
		short result = NumberUtils.doubleRoundToShort(Short.MIN_VALUE - 1);
		assertEquals(Short.MIN_VALUE, result);
	}
}
