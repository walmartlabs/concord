@import java.util.List;
@import com.walmartlabs.concord.console3.model.OrganizationEntry;

@param List<OrganizationEntry> organizations

@for(OrganizationEntry o : organizations)
    <div class="item">
        <div class="content">
            <a href="/#/org/${o.name()}">${o.name()}</a>
        </div>
    </div>
@endfor
