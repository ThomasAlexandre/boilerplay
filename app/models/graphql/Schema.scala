package models.graphql

import models.sandbox.SandboxSchema
import models.settings.SettingsSchema
import sangria.execution.deferred.DeferredResolver
import sangria.schema._

object Schema {
  val modelFetchers = {
    // Start model fetchers
    Nil
    // End model fetchers
  }

  val resolver = DeferredResolver.fetchers()

  val baseQueryFields = SettingsSchema.queryFields ++ SandboxSchema.queryFields

  val modelQueryFields = {
    // Start model query fields
    Nil
    // End model query fields
  }

  val queryType = ObjectType(
    name = "Query",
    description = "The main query interface.",
    fields = (modelQueryFields ++ baseQueryFields).sortBy(_.name)
  )

  val modelMutationFields = {
    // Start model mutation fields
    Nil
    // End model mutation fields
  }

  val mutationType = ObjectType(
    name = "Mutation",
    description = "The main mutation interface.",
    fields = (SandboxSchema.mutationFields ++ modelMutationFields).sortBy(_.name)
  )

  val schema = sangria.schema.Schema(
    query = queryType,
    mutation = Some(mutationType),
    subscription = None,
    additionalTypes = Nil
  )
}
