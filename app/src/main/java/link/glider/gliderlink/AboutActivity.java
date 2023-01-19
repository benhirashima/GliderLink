package link.glider.gliderlink;

import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;

public class AboutActivity extends UnlockedActivity
{

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_about);
		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		toolbar.setNavigationOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick (View v)
			{
				onBackPressed();
			}
		});

		TextView textView = findViewById(R.id.about_text);
		textView.setMovementMethod(LinkMovementMethod.getInstance());
		textView.setText(Html.fromHtml("<p>Version " + BuildConfig.VERSION_NAME + "</p>" + getString(R.string.intro_text) + getString(R.string.gotenna_description) + getString(R.string.visit_website_text) + getString(R.string.credits_text)));
	}
}
