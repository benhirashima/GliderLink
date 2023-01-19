package link.glider.gliderlink.utils;

import java.util.concurrent.atomic.AtomicLong;

public class AtomicDouble
{
	private final AtomicLong value = new AtomicLong();

	public AtomicDouble(double value)
	{
		set(value);
	}

	public double get()
	{
		return Double.longBitsToDouble(value.get());
	}

	public void set(double value)
	{
		this.value.set(Double.doubleToLongBits(value));
	}
}
