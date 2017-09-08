package models.graphql

import models.Application
import services.ServiceRegistry
import util.tracing.TraceData

case class GraphQLContext(app: Application, services: ServiceRegistry, trace: TraceData)
