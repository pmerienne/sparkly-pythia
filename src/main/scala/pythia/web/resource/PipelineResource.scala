package pythia.web.resource

import pythia.dao.PipelineRepository
import pythia.web.model.{ModelMapper, PipelineConfigurationModel}

class PipelineResource(
  implicit val pipelineRepository: PipelineRepository,
  implicit val modelMapper: ModelMapper) extends BaseResource {

  get("/") {
    pipelineRepository
      .all()
      .map(modelMapper.convert(_))
  }

  get("/:id") {
    val id = params("id")
    pipelineRepository.get(id) match {
      case Some(topology) => modelMapper.convert(topology)
      case None => halt(404)
    }
  }

  delete("/:id") {
    val id = params("id")
    pipelineRepository.delete(id)
  }

  put("/") {
    val topology = parsedBody.extract[PipelineConfigurationModel]
    pipelineRepository.store(topology.id, modelMapper.convert(topology))
  }

}