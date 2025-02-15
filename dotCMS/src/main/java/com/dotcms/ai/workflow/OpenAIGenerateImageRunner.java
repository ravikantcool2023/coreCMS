package com.dotcms.ai.workflow;

import com.dotcms.ai.app.ConfigService;
import com.dotcms.ai.service.OpenAIImageService;
import com.dotcms.ai.service.OpenAIImageServiceImpl;
import com.dotcms.ai.util.VelocityContextFactory;
import com.dotcms.api.web.HttpServletRequestThreadLocal;
import com.dotcms.contenttype.model.field.BinaryField;
import com.dotcms.contenttype.model.field.Field;
import com.dotcms.contenttype.model.type.ContentType;
import com.dotcms.rendering.velocity.util.VelocityUtil;
import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.portlets.contentlet.model.Contentlet;
import com.dotmarketing.portlets.workflows.model.WorkflowActionClassParameter;
import com.dotmarketing.portlets.workflows.model.WorkflowProcessor;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;
import com.dotmarketing.util.json.JSONObject;
import com.liferay.portal.model.User;
import io.vavr.control.Try;
import org.apache.velocity.context.Context;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;

/**
 * The OpenAIGenerateImageRunner class is responsible for running workflows that generate images using OpenAI.
 * It implements the AsyncWorkflowRunner interface and overrides its methods to provide the functionality needed.
 * This class is designed to handle long-running tasks in a separate thread and needs to be serialized to a persistent storage.
 */
public class OpenAIGenerateImageRunner implements AsyncWorkflowRunner {

    final String identifier;
    final long language;
    final User user;
    final String prompt;
    final boolean overwriteField;
    final String fieldToWrite;
    final long runAt;

    OpenAIGenerateImageRunner(WorkflowProcessor processor, Map<String, WorkflowActionClassParameter> params) {
        this(
                processor.getContentlet(),
                processor.getUser(),
                params.get(OpenAIParams.OPEN_AI_PROMPT.key).getValue(),
                Try.of(() -> Boolean.parseBoolean(params.get(OpenAIParams.OVERWRITE_FIELDS.key).getValue()))
                        .getOrElse(false),
                params.get(OpenAIParams.FIELD_TO_WRITE.key).getValue(),
                Try.of(() -> Integer.parseInt(params.get(OpenAIParams.RUN_DELAY.key).getValue())).getOrElse(5)
        );
    }

    OpenAIGenerateImageRunner(final Contentlet contentlet,
                              final User user,
                              final String prompt,
                              final boolean overwriteField,
                              final String fieldToWrite,
                              final int runDelay) {
        this.identifier = contentlet.getIdentifier();
        this.language = contentlet.getLanguageId();
        this.prompt = prompt;
        this.overwriteField = overwriteField;
        this.fieldToWrite = fieldToWrite;
        this.user = user;
        this.runAt = System.currentTimeMillis() + runDelay;
    }

    @Override
    public long getRunAt() {
        return this.runAt;
    }

    @Override
    public String getIdentifier() {
        return this.identifier;
    }

    @Override
    public long getLanguage() {
        return this.language;
    }

    public void runInternal() {
        final Contentlet workingContentlet = getLatest(identifier, language, user);
        final Host host = Try.of(
                        () -> APILocator.getHostAPI().find(workingContentlet.getHost(), APILocator.systemUser(), true))
                .getOrElse(APILocator.systemHost());

        final Optional<Field> fieldToTry = resolveField(workingContentlet);

        if (UtilMethods.isEmpty(prompt)) {
            Logger.info(OpenAIContentPromptActionlet.class, "no prompt found, returning");
            return;
        }

        Logger.info(this.getClass(), "Running OpenAI Generate Image Content for : " + workingContentlet.getTitle());

        if (fieldToTry.isEmpty()) {
            Logger.info(this.getClass(), "no binary field found, returning");
            return;
        }

        final Optional<Object> fieldVal = Try.of(
                        () -> APILocator.getContentletAPI().getFieldValue(workingContentlet, fieldToTry.get()))
                .toJavaOptional();
        if (fieldVal.isPresent() && UtilMethods.isSet(fieldVal.get()) && !overwriteField) {
            Logger.info(OpenAIContentPromptActionlet.class,
                    "field:" + fieldToTry.get().variable() + "  already set:" + fieldVal.get() + ", returning");
            return;
        }

        boolean setRequest = false;
        try {
            final Context ctx = VelocityContextFactory.getMockContext(workingContentlet, user);
            if (HttpServletRequestThreadLocal.INSTANCE.getRequest() == null) {
                setRequest = true;
                HttpServletRequestThreadLocal.INSTANCE.setRequest((HttpServletRequest) ctx.get("request"));
            }

            final String finalPrompt = VelocityUtil.eval(prompt, ctx);
            final OpenAIImageService service = new OpenAIImageServiceImpl(
                    ConfigService.INSTANCE.config(host),
                    user,
                    APILocator.getHostAPI(),
                    APILocator.getTempFileAPI());

            final JSONObject resp = Try.of(() -> service.sendTextPrompt(finalPrompt))
                    .onFailure(e -> Logger.warn(OpenAIGenerateImageRunner.class, "error generating image:" + e))
                    .getOrElse(JSONObject::new);

            final String tempFile = resp.optString("response");
            if (UtilMethods.isEmpty(tempFile)) {
                Logger.warn(
                        this.getClass(),
                        "Unable to generate image for contentlet: " + workingContentlet.getTitle());
                return;
            }

            final Contentlet contentToSave = checkoutLatest(identifier, language, user);
            contentToSave.setProperty(fieldToTry.get().variable(), tempFile);
            saveContentlet(contentToSave, user);
        } catch (Exception e) {
            handleError(e, user);
        } finally{
            if (setRequest) {
                HttpServletRequestThreadLocal.INSTANCE.setRequest(null);
            }
        }
    }

    private Optional<Field> resolveField(final Contentlet contentlet) {
        final ContentType type = contentlet.getContentType();
        final Optional<Field> fieldToTry = Try.of(() -> type.fieldMap().get(this.fieldToWrite)).toJavaOptional();
        if (UtilMethods.isSet(this.fieldToWrite)) {
            return fieldToTry;
        }

        return type.fields(BinaryField.class).stream().findFirst();
    }

}
