package it.niedermann.fis.parameter;

import it.niedermann.fis.FisConfiguration;
import it.niedermann.fis.main.api.ParameterApi;
import it.niedermann.fis.main.model.ClientConfigurationDto;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/api")
public class ParameterApiImpl implements ParameterApi {

    private final ClientConfigurationDto dto;

    public ParameterApiImpl(FisConfiguration config) {
        this.dto = config.client();
    }

    @Override
    public ResponseEntity<ClientConfigurationDto> getParameter(String ifNoneMatch) {
        return ResponseEntity.ok(dto);
    }
}
