package aoharureverie.ocaacrclient.oldchat.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import aoharureverie.ocaacrclient.oldchat.R;
import aoharureverie.ocaacrclient.oldchat.models.ResourceSection;

class ResourceSectionsActivityLifecycle extends ResourceSectionsActivitySupport2 {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resource_sections);

        lvSections = findViewByIdCompat(R.id.lvResourceSections);
        pbLoading = findViewByIdCompat(R.id.pbResourceSectionsLoading);
        tvQuota = findViewByIdCompat(R.id.tvResourceQuota);

        View btnBack = (View) findViewByIdCompat(R.id.btnResourceSectionsBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        View btnAdd = (View) findViewByIdCompat(R.id.btnResourceSectionsAdd);
        if (btnAdd != null) {
            btnAdd.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showQuotaCreateSectionDialog();
                }
            });
        }

        adapter = new ResourceSectionAdapter(this, sections);
        lvSections.setAdapter(adapter);
        lvSections.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= sections.size()) {
                    return;
                }
                openSection(sections.get(position));
            }
        });
        lvSections.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= sections.size()) {
                    return false;
                }
                ResourceSection section = sections.get(position);
                if (section == null || !canDeleteSection(section)) {
                    return false;
                }
                confirmDeleteSection(section);
                return true;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = getSharedPreferences(AUTH_PREFS, MODE_PRIVATE);
        token = prefs.getString("access_token", "");
        myUid = prefs.getString("my_uid", "");
        loadQuota();
        loadSections();
    }
}
